package tech.kzen.auto.client.objects.document.graph

import web.cssom.*
import emotion.react.css
import mui.material.IconButton
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.graph.DataflowWiring
import tech.kzen.auto.common.objects.document.graph.GraphDocument
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowRepository
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.persistentMapOf


//---------------------------------------------------------------------------------------------------------------------
external interface GraphControllerProps: Props {
    var attributeController: AttributeController.Wrapper
}


external interface GraphControllerState: State {
    var clientState: SessionState?
    var creating: Boolean

    var visualDataflowModel: VisualDataflowModel?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class GraphController(
    props: GraphControllerProps
):
    RPureComponent<GraphControllerProps, GraphControllerState>(props),
    InsertionGlobal.Subscriber,
    VisualDataflowRepository.Observer,
    SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val edgePipeName = "EdgePipe"
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        private val attributeController: AttributeController.Wrapper,
        private val ribbonController: RibbonController.Wrapper
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
                    GraphController::class.react {
                        this.attributeController = this@Wrapper.attributeController
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("ProjectController - Subscribed")
        async {
            ClientContext.sessionGlobal.observe(this)

//            ClientContext.mirroredGraphStore.observe(this)
            ClientContext.insertionGlobal.subscribe(this)
//            ClientContext.navigationGlobal.observe(this)
            ClientContext.visualDataflowRepository.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
//        ClientContext.mirroredGraphStore.unobserve(this)
//        ClientContext.executionManager.unsubscribe(this)
        ClientContext.insertionGlobal.unsubscribe(this)
//        ClientContext.navigationGlobal.unobserve(this)
        ClientContext.visualDataflowRepository.unobserve(this)
        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: GraphControllerProps,
            prevState: GraphControllerState,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)

        val documentPath = state.clientState?.navigationRoute?.documentPath
        if (documentPath != prevState.clientState?.navigationRoute?.documentPath) {
//            console.log("ProjectController componentDidUpdate", state.documentPath, prevState.documentPath)

            if (documentPath == null) {
                setState {
                    visualDataflowModel = null
                }
            }
            else {
                async {
                    val visualDataflowModel = ClientContext.visualDataflowRepository.get(documentPath)
                    setState {
                        this.visualDataflowModel = visualDataflowModel
                    }
                }
            }
        }
    }


    override fun onClientState(clientState: SessionState) {
        setState {
            this.clientState = clientState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeDataflowExecution(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ) {}


    override suspend fun onVisualDataflowModel(host: DocumentPath, visualDataflowModel: VisualDataflowModel) {
        if (state.clientState?.navigationRoute?.documentPath != host) {
            return
        }

        setState {
            this.visualDataflowModel = visualDataflowModel
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
//    override fun handleNavigation(
//            documentPath: DocumentPath?,
//            parameters: RequestParams
//    ) {
//        setState {
//            this.documentPath = documentPath
//        }
//    }


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

        val archetypeLocation = ClientContext.insertionGlobal.getAndClearSelection()
                ?: return

        val archetypeNotation = state.clientState!!.graphStructure().graphNotation.coalesce[archetypeLocation]!!
        val archetypeIs = archetypeNotation.get(NotationConventions.isAttributeName)?.asString()!!

        val containingObjectLocation = ObjectLocation(
                state.clientState?.navigationRoute?.documentPath!!, NotationConventions.mainObjectPath)

        val isPipe = archetypeIs == edgePipeName

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

                    val edgesNotation = DataflowMatrix.edgesNotation(documentNotation)

                    InsertListItemInAttributeCommand(
                            containingObjectLocation,
                            GraphDocument.edgesAttributePath,
                            PositionRelation.at(edgesNotation.values.size),
                            attributeNotation)
                }
                else {
                    val objectNotation = ObjectNotation
                            .ofParent(archetypeLocation.objectPath.name)
                            .upsertAttribute(CellCoordinate.rowAttributeName, ScalarAttributeNotation(row.toString()))
                            .upsertAttribute(CellCoordinate.columnAttributeName, ScalarAttributeNotation(column.toString()))

                    val verticesNotation = DataflowMatrix.verticesNotation(documentNotation)

                    InsertObjectInListAttributeCommand(
                        containingObjectLocation,
                        GraphDocument.verticesAttributePath,
                        PositionRelation.at(verticesNotation.values.size),
                        AutoConventions.randomAnonymous(),
                        PositionRelation.at(documentNotation.objects.notations.values.size),
                        objectNotation)
                }

        async {
            ClientContext.mirroredGraphStore.apply(command)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val documentNotation = documentNotation()
                ?: return

//        +"QueryController documentPath: ${state.documentPath}"
        renderGraph(documentNotation)
        runController()
    }


    private fun ChildrenBuilder.renderGraph(
            documentNotation: DocumentNotation
    ) {
        val verticesNotation = DataflowMatrix.verticesNotation(documentNotation)
        val edgesNotation = DataflowMatrix.edgesNotation(documentNotation)
        val dataflowMatrix = DataflowMatrix.cellDescriptorLayers(
                state.clientState!!.graphStructure(),  verticesNotation, edgesNotation)

        if (dataflowMatrix.isEmpty()) {
            div {
                css {
                    paddingTop = 2.em
                    paddingLeft = 2.em
//                    backgroundColor = Color.blue
                }

                div {
                    css {
                        fontSize = 1.5.em
                    }
                    +"Empty graph, please add a source from the toolbar (above)"
                }

                insertionPoint(0, 0)
            }
        }
        else {
            div {
                css {
                    paddingTop = 2.em
                    paddingLeft = 2.em
//                    backgroundColor = Color.blue
                }

                val visualDataflowModel = state.visualDataflowModel
                        ?: VisualDataflowModel.empty

                nonEmptyDag(
                        state.clientState!!,
                        visualDataflowModel,
                        dataflowMatrix)
            }
        }
    }


    private fun ChildrenBuilder.nonEmptyDag(
            clientState: SessionState,
            visualDataflowModel: VisualDataflowModel,
            dataflowMatrix: DataflowMatrix
    ) {
        val dataflowDag = DataflowDag.of(dataflowMatrix)

        var colspanRemaining = 0
        table {
            css {
                // https://stackoverflow.com/a/24594811/1941359
                height = 100.pct
            }

            tbody {
                for (row in 0 .. dataflowMatrix.usedRows) {
                    tr {
                        for (column in 0 .. dataflowMatrix.usedColumns) {
                            if (colspanRemaining > 0) {
                                colspanRemaining--
                                continue
                            }

                            td {
                                css {
//                                    borderStyle = BorderStyle.solid
//                                    borderColor = Color.black
//                                    borderWidth = 1.px

                                    verticalAlign = VerticalAlign.top
                                    height = 100.pct
                                }

                                val cellDescriptor = dataflowMatrix.get(row, column)

                                if (cellDescriptor == null) {
                                    key = "$row-$column"
                                    absentCell(row, column)
                                }
                                else {
                                    key = cellDescriptor.key()

                                    if (cellDescriptor is VertexDescriptor) {
                                        val cellMetadata = clientState.graphStructure()
                                                .graphMetadata.objectMetadata[cellDescriptor.objectLocation]!!

                                        val inputAttributes: List<AttributeName> = cellMetadata
                                                .attributes
                                                .values
                                                .filter {
                                                    DataflowWiring.isInput(it.value.attributeMetadataNotation)
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
                                            visualDataflowModel,
                                            dataflowMatrix,
                                            dataflowDag)
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

//            +"[$row, $column]"
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
                css {
                    if (! state.creating) {
                        opacity = number(0.0)
                        cursor = Cursor.default
                    }
                }

                onClick = {
                    onCreate(row, column)
                }

                AddCircleOutlineIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.cell(
            cellDescriptor: CellDescriptor,
            clientState: SessionState,
            visualDataflowModel: VisualDataflowModel,
            dataflowMatrix: DataflowMatrix,
            dataflowDag: DataflowDag
    ) {
        CellController::class.react {
            this.attributeController = props.attributeController

            this.cellDescriptor = cellDescriptor

            attributeNesting = AttributeNesting(persistentListOf(
                AttributeSegment.ofIndex(cellDescriptor.indexInContainer)))

            documentPath = clientState.navigationRoute.documentPath!!

            this.clientState = clientState
            this.visualDataflowModel = visualDataflowModel
            this.dataflowMatrix = dataflowMatrix
            this.dataflowDag = dataflowDag
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.runController() {
        div {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            GraphRunController::class.react {
                documentPath = state.clientState?.navigationRoute?.documentPath
                graphStructure = state.clientState?.graphStructure()
                visualDataflowModel = state.visualDataflowModel
            }
        }
    }
}