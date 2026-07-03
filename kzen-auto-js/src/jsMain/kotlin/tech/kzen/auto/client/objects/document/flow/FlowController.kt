package tech.kzen.auto.client.objects.document.flow

import emotion.react.css
import mui.material.IconButton
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.bridge.InsertionKey
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.ExecutionIntentGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.common.objects.document.flow.FlowConventions
import tech.kzen.auto.common.objects.document.flow.FlowWiring
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.VertexDescriptor
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.persistentMapOf
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface FlowControllerProps: Props {
    var attributeController: AttributeEditorManager.Wrapper

    var clientStateGlobal: ClientStateGlobal
    var executionIntentGlobal: ExecutionIntentGlobal
    var mirroredGraphStore: MirroredGraphStore
    var restClient: ClientRestApi
    var objectStableMapper: ObjectStableMapper
}


external interface FlowControllerState: State {
    var clientState: ClientState?
    var creating: Boolean

    var visualFlowModel: VisualFlowModel?
}


//---------------------------------------------------------------------------------------------------------------------
// The modernized "graph" / "time series" UI. Renders the same vertex/edge grid as the legacy
// GraphController (reusing CellController), but sources its VisualFlowModel from the logic trace
// store (via FlowProgressStore) rather than the retired VisualDataflowRepository, and relies on the
// global logic ribbon (HeaderRunController) for Run / Step / Pause / Stop instead of a bespoke FAB.
@Suppress("unused")
class FlowController(
    props: FlowControllerProps
):
    RPureComponent<FlowControllerProps, FlowControllerState>(props),
    InsertionGlobal.Subscriber,
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        private val attributeController: AttributeEditorManager.Wrapper,
        private val ribbonController: RibbonController.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val executionIntentGlobal: ExecutionIntentGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val restClient: ClientRestApi,
        @Service private val objectStableMapper: ObjectStableMapper
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    ribbonController.child(this) {}
                }
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    FlowController::class.react {
                        this.attributeController = this@Wrapper.attributeController
                        this.clientStateGlobal = this@Wrapper.clientStateGlobal
                        this.executionIntentGlobal = this@Wrapper.executionIntentGlobal
                        this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        this.restClient = this@Wrapper.restClient
                        this.objectStableMapper = this@Wrapper.objectStableMapper
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val flowProgressStore by lazy {
        FlowProgressStore(props.restClient, props.objectStableMapper)
    }

    // Refetch the trace-derived visual model only when the document or the run status changes.
    private var lastFetchKey: String? = null


    init {
        installContextType(DocumentBridgeContext)
    }

    private fun bridge(): DocumentBridge? =
        contextValue<DocumentBridge?>()


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            props.clientStateGlobal.observe(this)
            bridge()?.channel(InsertionKey)?.subscribe(this)
        }
    }


    override fun componentWillUnmount() {
        bridge()?.channel(InsertionKey)?.unsubscribe(this)
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        setState {
            this.clientState = clientState
        }

        refreshVisualModelIfNeeded(clientState)
    }


    private fun refreshVisualModelIfNeeded(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (! FlowConventions.isFlow(documentNotation)) {
            return
        }

        // Key on the run status time so each logic-status poll during a run refreshes the live model,
        // and on the document path so opening a flow loads its last run's final state.
        val logicStatusTime = clientState.clientLogicState.logicStatus?.time
        val fetchKey = "${documentPath.asString()}|$logicStatusTime"
        if (fetchKey == lastFetchKey) {
            return
        }
        lastFetchKey = fetchKey

        val matrix = FlowMatrix.ofDocument(documentPath, clientState.graphStructure())
        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        val vertexLocations = matrix.verticesByLocation.keys.toList()

        val activeRun = clientState.clientLogicState.logicStatus?.active

        async {
            val visualFlowModel = flowProgressStore.fetchVisualModel(mainLocation, vertexLocations, activeRun)
            setState {
                this.visualFlowModel = visualFlowModel
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onInsertionSelected(action: ObjectLocation) {
        setState {
            creating = true
        }
    }


    override fun onInsertionUnselected() {
        setState {
            creating = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun documentNotation(): DocumentNotation? {
        val graphStructure = state.clientState?.graphStructure()
            ?: return null

        val documentPath = state.clientState?.navigationRoute?.documentPath
            ?: return null

        return graphStructure
            .graphNotation
            .documents[documentPath]
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCreate(
        row: Int,
        column: Int
    ) {
        val documentNotation = documentNotation()
            ?: return

        val archetypeLocation = bridge()?.channel(InsertionKey)?.getAndClearSelection()
            ?: return

        val graphNotation = state.clientState!!.graphStructure().graphNotation
        val archetypeNotation = graphNotation.coalesce[archetypeLocation]!!

        val containingObjectLocation = ObjectLocation(
            state.clientState?.navigationRoute?.documentPath!!, NotationConventions.mainObjectPath)

        val isPipe = FlowConventions.isPipeArchetype(graphNotation, archetypeLocation)

        val command =
            if (isPipe) {
                val orientationName = archetypeNotation
                    .get(EdgeDescriptor.orientationAttributeName)
                    ?.asString()!!

                val attributeNotation = MapAttributeNotation(persistentMapOf(
                    EdgeDescriptor.orientationAttributeSegment to ScalarAttributeNotation(orientationName),
                    CellCoordinate.rowAttributeSegment to ScalarAttributeNotation(row.toString()),
                    CellCoordinate.columnAttributeSegment to ScalarAttributeNotation(column.toString())
                ))

                val edgesNotation = FlowMatrix.edgesNotation(documentNotation)

                InsertListItemInAttributeCommand(
                    containingObjectLocation,
                    FlowConventions.edgesAttributePath,
                    PositionRelation.at(edgesNotation.values.size),
                    attributeNotation)
            }
            else {
                val objectNotation = ObjectNotation
                    .ofParent(archetypeLocation.objectPath.name)
                    .upsertAttribute(CellCoordinate.rowAttributeName, ScalarAttributeNotation(row.toString()))
                    .upsertAttribute(CellCoordinate.columnAttributeName, ScalarAttributeNotation(column.toString()))

                val verticesNotation = FlowMatrix.verticesNotation(documentNotation)

                InsertObjectInListAttributeCommand(
                    containingObjectLocation,
                    FlowConventions.verticesAttributePath,
                    PositionRelation.at(verticesNotation.values.size),
                    AutoConventions.randomAnonymous(),
                    PositionRelation.at(documentNotation.objects.notations.map.size),
                    objectNotation)
            }

        async {
            props.mirroredGraphStore.apply(command)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val documentNotation = documentNotation()
            ?: return

        renderGraph(documentNotation)
    }


    private fun ChildrenBuilder.renderGraph(
        documentNotation: DocumentNotation
    ) {
        val verticesNotation = FlowMatrix.verticesNotation(documentNotation)
        val edgesNotation = FlowMatrix.edgesNotation(documentNotation)
        val flowMatrix = FlowMatrix.cellDescriptorLayers(
            state.clientState!!.graphStructure(), verticesNotation, edgesNotation)

        if (flowMatrix.isEmpty()) {
            div {
                css {
                    paddingTop = 2.em
                    paddingLeft = 2.em
                }

                div {
                    css {
                        fontSize = 1.5.em
                    }
                    +"Empty flow, please add an input or source from the toolbar (above)"
                }

                insertionPoint(0, 0)
            }
        }
        else {
            div {
                css {
                    paddingTop = 2.em
                    paddingLeft = 2.em
                }

                val visualFlowModel = state.visualFlowModel
                    ?: VisualFlowModel.empty

                nonEmptyDag(
                    state.clientState!!,
                    visualFlowModel,
                    flowMatrix)
            }
        }
    }


    private fun ChildrenBuilder.nonEmptyDag(
        clientState: ClientState,
        visualFlowModel: VisualFlowModel,
        flowMatrix: FlowMatrix
    ) {
        val flowDag = FlowDag.of(flowMatrix)

        var colspanRemaining = 0
        table {
            css {
                height = 100.pct
            }

            tbody {
                for (row in 0 .. flowMatrix.usedRows) {
                    tr {
                        for (column in 0 .. flowMatrix.usedColumns) {
                            if (colspanRemaining > 0) {
                                colspanRemaining--
                                continue
                            }

                            td {
                                css {
                                    verticalAlign = VerticalAlign.top
                                    height = 100.pct
                                }

                                val cellDescriptor = flowMatrix.get(row, column)

                                if (cellDescriptor == null) {
                                    key = Key("$row-$column")
                                    absentCell(row, column)
                                }
                                else {
                                    key = Key(cellDescriptor.key())

                                    if (cellDescriptor is VertexDescriptor) {
                                        val cellMetadata = clientState.graphStructure()
                                            .graphMetadata.objectMetadata[cellDescriptor.objectLocation]!!

                                        val inputAttributes: List<AttributeName> = cellMetadata
                                            .attributes
                                            .map
                                            .filter {
                                                FlowWiring.isInput(it.value.attributeMetadataNotation)
                                            }
                                            .map {
                                                it.key
                                            }

                                        if (inputAttributes.size > 1) {
                                            colspanRemaining = inputAttributes.size - 1
                                            colSpan = inputAttributes.size
                                        }
                                    }

                                    cell(cellDescriptor,
                                        clientState,
                                        visualFlowModel,
                                        flowMatrix,
                                        flowDag)
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.absentCell(
        row: Int,
        column: Int
    ) {
        div {
            css {
                width = 100.pct
                height = 100.pct
                textAlign = TextAlign.center
                verticalAlign = VerticalAlign.middle
            }

            div {
                css {
                    height = 50.pct.minus(2.em)
                }
            }
            insertionPoint(row, column)
        }
    }


    private fun ChildrenBuilder.insertionPoint(row: Int, column: Int) {
        span {
            if (state.creating) {
                title = "Insert here"
            }

            IconButton {
                sx {
                    if (!state.creating) {
                        opacity = number(0.0)
                        cursor = Cursor.default
                    }
                }

                onClick = {
                    onCreate(row, column)
                }

                icon("material-symbols:add-circle-outline") {}
            }
        }
    }


    private fun ChildrenBuilder.cell(
        cellDescriptor: CellDescriptor,
        clientState: ClientState,
        visualFlowModel: VisualFlowModel,
        flowMatrix: FlowMatrix,
        flowDag: FlowDag
    ) {
        CellController::class.react {
            this.attributeController = props.attributeController
            this.executionIntentGlobal = props.executionIntentGlobal
            this.mirroredGraphStore = props.mirroredGraphStore

            this.cellDescriptor = cellDescriptor

            attributeNesting = AttributeNesting(persistentListOf(
                AttributeSegment.ofIndex(cellDescriptor.indexInContainer)))

            documentPath = clientState.navigationRoute.documentPath!!

            this.clientState = clientState
            this.visualFlowModel = visualFlowModel
            this.flowMatrix = flowMatrix
            this.flowDag = flowDag
        }
    }
}
