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
import tech.kzen.auto.client.service.logic.LogicRunFrames
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.common.objects.document.flow.FlowConventions
import tech.kzen.auto.common.objects.document.flow.FlowStructureValidator
import tech.kzen.auto.common.objects.document.flow.FlowWiring
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.VertexDescriptor
import tech.kzen.auto.common.paradigm.flow.util.FlowUtils
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
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
    var logicValidationGlobal: LogicValidationGlobal
    var executionIntentGlobal: ExecutionIntentGlobal
    var mirroredGraphStore: MirroredGraphStore
    var restClient: ClientRestApi
    var objectStableMapper: ObjectStableMapper
}


// The consumed subset of ClientState, never the whole thing (docs/js-architecture.md §2): ClientStateGlobal
// replaces graphDefinitionAttempt only on notation events and navigationRoute only on navigation, so both
// references survive the ~1/s logic-status publishes of any active run — and RPureComponent's shallow-equal
// bails on them instead of repainting the whole grid.
external interface FlowControllerState: State {
    var graphStructure: GraphStructure?
    var documentPath: DocumentPath?
    var creating: Boolean

    var visualFlowModel: VisualFlowModel?

    // The pre-run structure lint (FlowStructureValidator) findings, computed on the state-derivation path rather
    // than in render — publishing to LogicValidationGlobal from render would setState on HeaderRunController
    // mid-render. Render reuses this; the first finding also disables Run via the global. Null before the first
    // computation.
    var structureFindings: List<String>?
}


//---------------------------------------------------------------------------------------------------------------------
// The Flow document UI: a vertex/edge grid (CellController) whose per-vertex VisualFlowModel is
// rebuilt from the logic trace store (via FlowProgressStore); run control comes from the global
// logic ribbon (HeaderRunController).
@Suppress("unused")
class FlowController(
    props: FlowControllerProps
):
    RPureComponent<FlowControllerProps, FlowControllerState>(props),
    InsertionGlobal.Subscriber,
    ClientStateGlobal.DocumentScopedObserver
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        private val attributeController: AttributeEditorManager.Wrapper,
        private val ribbonController: RibbonController.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val logicValidationGlobal: LogicValidationGlobal,
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
                        this.logicValidationGlobal = this@Wrapper.logicValidationGlobal
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

    // Recompute the (synchronous) structure findings only when this Flow's own notation actually changed —
    // mirrors JobController.lastValidationNotation (progress polls and unrelated client-state publishes reuse the
    // instance, so a reference compare suffices).
    private var lastStructureNotation: DocumentNotation? = null


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
            graphStructure = clientState.graphStructure()
            documentPath = clientState.navigationRoute.documentPath
        }

        refreshVisualModelIfNeeded(clientState)
        refreshStructureFindingsIfNeeded(clientState)
    }


    // The same pre-run structure lint FlowLogicCompiler refuses to compile on, computed here (not in render) so
    // it can both feed the render banner and — via LogicValidationGlobal — disable Run when the flow is
    // structurally broken (previously it only warned; Run stayed enabled). Synchronous, so the validation channel
    // is never in-flight.
    private fun refreshStructureFindingsIfNeeded(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val graphStructure = clientState.graphStructure()
        val documentNotation = graphStructure.graphNotation.documents[documentPath]
            ?: return

        if (!FlowConventions.isFlow(documentNotation)) {
            return
        }

        if (documentNotation === lastStructureNotation) {
            return
        }
        lastStructureNotation = documentNotation

        val verticesNotation = FlowMatrix.verticesNotation(documentNotation)
        val edgesNotation = FlowMatrix.edgesNotation(documentNotation)
        val flowMatrix = FlowMatrix.cellDescriptorLayers(graphStructure, verticesNotation, edgesNotation)

        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        val findings = FlowStructureValidator.validate(
            mainLocation, graphStructure.graphNotation, flowMatrix)

        if (findings != state.structureFindings) {
            setState {
                structureFindings = findings
            }
        }

        props.logicValidationGlobal.validation(
            documentPath, inFlight = false,
            errors = findings.map { LogicValidationGlobal.ValidationErrorLine(mainLocation, it) })
    }


    private fun refreshVisualModelIfNeeded(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (!FlowConventions.isFlow(documentNotation)) {
            return
        }

        val clientLogicState = clientState.clientLogicState
        val activeRun = clientLogicState.logicStatus?.active
        val involved = LogicRunFrames.frameForDocument(activeRun?.frame, documentPath) != null

        // Three-mode fetch key. traceVersion() alone is already version-gated (an idle or paused run stops
        // refetching entirely), but it is global to the single active run — so an UNRELATED run's per-emit
        // advance would otherwise re-key every visible Flow tab ~1/s and re-fetch its settled snapshot for
        // that whole run. The involvement gate is what scopes it to this document.
        val fetchKey = when {
            // This document is live in the run (its own, or hosted as a child): per-emit refresh —
            // traceVersion() moves exactly when a new trace value can exist.
            involved ->
                "${documentPath.asString()}|involved|${clientLogicState.traceVersion()}"

            // Some OTHER document's run is active: exactly one refetch at that run's start (starting a run
            // clears the prior retained trace, so this repaints — likely to empty), then nothing until the run
            // settles or this document joins the frame tree (at which point the mode switches to `involved`).
            activeRun != null ->
                "${documentPath.asString()}|other|${activeRun.id.value}"

            // No active run: structureVersion moves on run settle and on "Clear all traces" (even with no run),
            // so post-run final state and clear-to-empty both repaint.
            else ->
                "${documentPath.asString()}|idle|${clientLogicState.structureVersion()}"
        }

        if (fetchKey == lastFetchKey) {
            return
        }
        lastFetchKey = fetchKey

        val matrix = FlowMatrix.ofDocument(documentPath, clientState.graphStructure())
        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        val vertexLocations = matrix.verticesByLocation.keys.toList()

        async {
            val visualFlowModel = flowProgressStore.fetchVisualModel(mainLocation, vertexLocations, activeRun)

            // Each fetch allocates a fresh VisualFlowModel, so an unchanged snapshot would still re-render the
            // whole grid without this value-equal guard (it's a data class — `==` is cheap and meaningful).
            if (visualFlowModel == state.visualFlowModel) {
                return@async
            }

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
        val graphStructure = state.graphStructure
            ?: return null

        val documentPath = state.documentPath
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

        val graphNotation = state.graphStructure!!.graphNotation
        val archetypeNotation = graphNotation.coalesce[archetypeLocation]!!

        val containingObjectLocation = ObjectLocation(
            state.documentPath!!, NotationConventions.mainObjectPath)

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
            state.graphStructure!!, verticesNotation, edgesNotation)

        renderStructureFindings()

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
                    visualFlowModel,
                    flowMatrix)
            }
        }
    }


    // The same pre-run structure lint FlowLogicCompiler refuses to compile on, surfaced the moment
    // the mistake is made — a misplaced pipe otherwise silently rewires or disconnects the flow. Findings are
    // computed on the state-derivation path (refreshStructureFindingsIfNeeded) and reused here.
    //
    // NB: this container `div` is ALWAYS emitted (left empty when there are no findings) so the grid
    //     rendered after it keeps a stable child index across finding toggles — mirrors
    //     StageController.renderDefinitionErrors (see the remount rationale there).
    private fun ChildrenBuilder.renderStructureFindings() {
        val findings = state.structureFindings ?: listOf()

        div {
            if (findings.isEmpty()) {
                return@div
            }

            css {
                margin = 1.em
                padding = 0.5.em
                color = NamedColor.red
                borderWidth = 1.px
                borderStyle = LineStyle.solid
                borderColor = NamedColor.red
                borderRadius = 4.px
            }

            div {
                css {
                    fontWeight = FontWeight.bold
                }
                +"This flow has a structure error and can't run until it's fixed"
            }

            for (finding in findings) {
                div {
                    key = Key(finding)
                    css {
                        marginTop = 0.25.em
                    }
                    +finding
                }
            }
        }
    }


    private fun ChildrenBuilder.nonEmptyDag(
        visualFlowModel: VisualFlowModel,
        flowMatrix: FlowMatrix
    ) {
        val graphStructure = state.graphStructure!!
        val flowDag = FlowDag.of(flowMatrix)

        // Routing derived ONCE per render and threaded down as props — never recomputed per cell (each per-cell
        // FlowUtils.next used to rebuild FlowMatrix + FlowDag from notation: O(V²)-scale work per grid paint).
        val nextToRun = FlowUtils.next(flowMatrix, flowDag, visualFlowModel)
        val runningVertex = visualFlowModel.running()

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
                                        val cellMetadata = graphStructure
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
                                        graphStructure,
                                        visualFlowModel,
                                        flowMatrix,
                                        flowDag,
                                        nextToRun,
                                        runningVertex)
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
        graphStructure: GraphStructure,
        visualFlowModel: VisualFlowModel,
        flowMatrix: FlowMatrix,
        flowDag: FlowDag,
        nextToRun: ObjectLocation?,
        runningVertex: ObjectLocation?
    ) {
        CellController::class.react {
            this.attributeController = props.attributeController
            this.executionIntentGlobal = props.executionIntentGlobal
            this.mirroredGraphStore = props.mirroredGraphStore

            this.cellDescriptor = cellDescriptor

            attributeNesting = AttributeNesting(persistentListOf(
                AttributeSegment.ofIndex(cellDescriptor.indexInContainer)))

            documentPath = state.documentPath!!

            this.graphStructure = graphStructure
            this.visualFlowModel = visualFlowModel
            this.flowMatrix = flowMatrix
            this.flowDag = flowDag
            this.nextToRun = nextToRun
            this.runningVertex = runningVertex
        }
    }
}
