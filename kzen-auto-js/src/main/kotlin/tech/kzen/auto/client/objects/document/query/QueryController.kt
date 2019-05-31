package tech.kzen.auto.client.objects.document.query

import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.table
import react.dom.tbody
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.NameConventions
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.query.QueryDocument
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowManager
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.persistentListOf


@Suppress("unused")
class QueryController:
//        RComponent<RProps, QueryController.State>(),
        RPureComponent<RProps, QueryController.State>(),
        GraphStructureManager.Observer,
//        ExecutionManager.Observer,
        InsertionManager.Observer,
        NavigationManager.Observer,
        VisualDataflowManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var documentPath: DocumentPath?,
            var graphStructure: GraphStructure?,
            var creating: Boolean,

            var visualDataflowModel: VisualDataflowModel?
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val type: DocumentArchetype
    ):
            DocumentController
    {
        override fun type(): DocumentArchetype {
            return type
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(QueryController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("ProjectController - Subscribed")
        async {
            ClientContext.modelManager.observe(this)
//            ClientContext.executionManager.subscribe(this)
            ClientContext.insertionManager.subscribe(this)
            ClientContext.navigationManager.observe(this)

            ClientContext.visualDataflowManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
//        ClientContext.executionManager.unsubscribe(this)
        ClientContext.insertionManager.unSubscribe(this)
        ClientContext.navigationManager.unobserve(this)
        ClientContext.visualDataflowManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: RProps,
            prevState: State,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)

        if (state.documentPath != prevState.documentPath) {
            state.documentPath?.let {
                async {
                    val visualDataflowModel = ClientContext.visualDataflowManager.get(it)

                    setState {
                        this.visualDataflowModel = visualDataflowModel
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(graphStructure: GraphStructure, event: NotationEvent?) {
        setState {
            this.graphStructure = graphStructure
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeDataflowExecution(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ) {}


    override suspend fun onVisualDataflowModel(host: DocumentPath, visualDataflowModel: VisualDataflowModel) {
        if (state.documentPath != host) {
            return
        }

        setState {
            this.visualDataflowModel = visualDataflowModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun beforeExecution(objectLocation: ObjectLocation) {}
//
//
//    override suspend fun onExecutionModel(executionModel: ExecutionModel) {
//        setState {
//            execution = executionModel
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onSelected(action: ObjectLocation) {
        setState {
            creating = true
        }
    }


    override fun onUnselected() {
        setState {
            creating = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?) {
        setState {
            this.documentPath = documentPath
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
                .documents
                .get(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCreate(
            row: Int,
            column: Int
    ) {
        val archetypeLocation = ClientContext.insertionManager.getAndClearSelection()
                ?: return

        val documentNotation = documentNotation()
                ?: return

        val verticesNotation = DataflowMatrix.verticesNotation(documentNotation)
                ?: return

        val objectNotation = ObjectNotation
                .ofParent(archetypeLocation.objectPath.name)
                .upsertAttribute(CellCoordinate.rowAttributeName, ScalarAttributeNotation(row.toString()))
                .upsertAttribute(CellCoordinate.columnAttributeName, ScalarAttributeNotation(column.toString()))

        val containingObjectLocation = ObjectLocation(
                state.documentPath!!, NotationConventions.mainObjectPath)

        val command = InsertObjectInListAttributeCommand(
                containingObjectLocation,
                QueryDocument.verticesAttributePath,
                PositionIndex(verticesNotation.values.size),
                NameConventions.randomAnonymous(),
                PositionIndex(documentNotation.objects.values.size),
                objectNotation
        )

        async {
            ClientContext.commandBus.apply(command)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val documentNotation = documentNotation()
                ?: return

        renderGraph(documentNotation)

        runController()
    }


    private fun RBuilder.renderGraph(
            documentNotation: DocumentNotation
    ) {
        val vertexInfos =
                DataflowMatrix.verticesNotation(documentNotation)?.let {
                    DataflowMatrix.vertexInfoLayers(state.graphStructure!!.graphNotation,  it)
                }

        if (vertexInfos?.isEmpty() != false) {
            styledH3 {
                css {
                    paddingTop = 1.em
                }

                +"Empty query, please add a source from the toolbar (above)"
            }

            insertionPoint(0, 0)
        }
        else {
            styledDiv {
                css {
                    paddingTop = 2.em
//                    backgroundColor = Color.blue
                }

                val visualDataflowModel = state.visualDataflowModel
                        ?: VisualDataflowModel.empty

                nonEmptyDag(
                        state.graphStructure!!,
                        visualDataflowModel,
                        vertexInfos)
            }
        }
    }


    private fun RBuilder.nonEmptyDag(
            graphStructure: GraphStructure,
            visualDataflowModel: VisualDataflowModel,
            vertexMatrix: DataflowMatrix
    ) {
        val dataflowDag = DataflowDag.of(vertexMatrix)

        table {
            tbody {
                for (row in 0 .. vertexMatrix.usedRows) {
                    tr {
                        for (column in 0 .. vertexMatrix.usedColumns) {
                            styledTd {
                                css {
                                    padding(1.em)
//                                    borderStyle = BorderStyle.solid
//                                    borderColor = Color.black
//                                    borderWidth = 1.px
                                }

                                val vertexInfo = vertexMatrix.get(row, column)
                                if (vertexInfo == null) {
                                    absentVertex(row, column)
                                }
                                else {
                                    vertex(vertexInfo,
                                            graphStructure,
                                            visualDataflowModel,
                                            dataflowDag)
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.absentVertex(
            row: Int,
            column: Int
    ) {
        styledDiv {
            css {
                width = 100.pct
                textAlign = TextAlign.center
            }

//            +"[$row, $column]"
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

//            +"Index: $index"

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


    private fun RBuilder.vertex(
            vertexDescriptor: VertexDescriptor,
            graphStructure: GraphStructure,
            visualDataflowModel: VisualDataflowModel,
            dataflowDag: DataflowDag
    ) {
        child(VertexController::class) {
            key = vertexDescriptor.objectLocation.toReference().asString()

            attrs {
                attributeNesting = AttributeNesting(persistentListOf(
                        AttributeSegment.ofIndex(vertexDescriptor.indexInVertices)))

                this.objectLocation = vertexDescriptor.objectLocation
                this.graphStructure = graphStructure

//                this.visualVertexModel = visualDataflowModel.vertices[vertexInfo.objectLocation]
//                        ?: VisualVertexModel.empty

                this.visualDataflowModel = visualDataflowModel
                this.dataflowDag = dataflowDag
//                        ?: throw IllegalStateException(
//                                "Visual vertex state missing: ${vertexInfo.objectLocation}")
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

            child(QueryRunController::class) {
                attrs {
                    documentPath = state.documentPath
                    graphStructure = state.graphStructure
                }
            }
        }
    }
}