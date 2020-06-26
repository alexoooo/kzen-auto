package tech.kzen.auto.client.objects.document.graph

import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.tbody
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
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


@Suppress("unused")
class GraphController:
    RPureComponent<GraphController.Props, GraphController.State>(),
//        LocalGraphStore.Observer,
    InsertionGlobal.Subscriber,
//        NavigationGlobal.Observer,
    VisualDataflowRepository.Observer,
    SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val edgePipeName = "EdgePipe"
    }


    class Props(
            var attributeController: AttributeController.Wrapper
    ): RProps


    class State(
//            var documentPath: DocumentPath?,
//            var graphStructure: GraphStructure?,
            var clientState: SessionState?,
            var creating: Boolean,

            var visualDataflowModel: VisualDataflowModel?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            private val archetype: ObjectLocation,
            private val attributeController: AttributeController.Wrapper
    ):
            DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(GraphController::class) {
                attrs {
                    this.attributeController = this@Wrapper.attributeController
                }

                handler()
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
            prevProps: Props,
            prevState: State,
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
                            PositionIndex(edgesNotation.values.size),
                            attributeNotation
                    )
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
                            PositionIndex(verticesNotation.values.size),
                            AutoConventions.randomAnonymous(),
                            PositionIndex(documentNotation.objects.notations.values.size),
                            objectNotation
                    )
                }

        async {
            ClientContext.mirroredGraphStore.apply(command)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val documentNotation = documentNotation()
                ?: return

//        +"QueryController documentPath: ${state.documentPath}"

        renderGraph(documentNotation)

        runController()
    }


    private fun RBuilder.renderGraph(
            documentNotation: DocumentNotation
    ) {
        val verticesNotation = DataflowMatrix.verticesNotation(documentNotation)
        val edgesNotation = DataflowMatrix.edgesNotation(documentNotation)
        val dataflowMatrix = DataflowMatrix.cellDescriptorLayers(
                state.clientState!!.graphStructure(),  verticesNotation, edgesNotation)

        if (dataflowMatrix.isEmpty()) {
            styledDiv {
                css {
                    paddingTop = 2.em
                    paddingLeft = 2.em
//                    backgroundColor = Color.blue
                }

                styledDiv {
                    css {
                        fontSize = 1.5.em
                    }
                    +"Empty graph, please add a source from the toolbar (above)"
                }

                insertionPoint(0, 0)
            }
        }
        else {
            styledDiv {
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


    private fun RBuilder.nonEmptyDag(
            clientState: SessionState,
            visualDataflowModel: VisualDataflowModel,
            dataflowMatrix: DataflowMatrix
    ) {
        val dataflowDag = DataflowDag.of(dataflowMatrix)

        var colspanRemaining = 0
        styledTable {
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

                            styledTd {
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
                                            attrs {
                                                colSpan = inputAttributes.size.toString()
                                            }
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


    private fun RBuilder.absentCell(
            row: Int,
            column: Int
    ) {
        styledDiv {
            css {
                width = 100.pct
                height = 100.pct
                textAlign = TextAlign.center
                verticalAlign = VerticalAlign.middle
            }

//            +"[$row, $column]"
            styledDiv {
                css {
                    height = 50.pct.minus(2.em)
                }
            }
            insertionPoint(row, column)
        }
    }


    private fun RBuilder.insertionPoint(row: Int, column: Int) {
        styledSpan {
            attrs {
                if (state.creating) {
                    title = "Insert here"
                }
            }

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        if (! state.creating) {
                            opacity = 0
                            cursor = Cursor.default
                        }
                    }

                    onClick = {
                        onCreate(row, column)
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }
        }
    }


    private fun RBuilder.cell(
            cellDescriptor: CellDescriptor,
            clientState: SessionState,
            visualDataflowModel: VisualDataflowModel,
            dataflowMatrix: DataflowMatrix,
            dataflowDag: DataflowDag
    ) {
        child(CellController::class) {
            attrs {
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.runController() {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            child(GraphRunController::class) {
                attrs {
                    documentPath = state.clientState?.navigationRoute?.documentPath
                    graphStructure = state.clientState?.graphStructure()
                    visualDataflowModel = state.visualDataflowModel
                }
            }
        }
    }
}