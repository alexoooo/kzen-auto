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
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.structure.VertexInfo
import tech.kzen.auto.common.paradigm.dataflow.model.structure.VertexMatrix
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowManager
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.model.*
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
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
//        ClientContext.executionManager.unsubscribe(this)
        ClientContext.insertionManager.unSubscribe(this)
        ClientContext.navigationManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: RProps,
            prevState: State,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)
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


    private fun verticesNotation(
            documentNotation: DocumentNotation
    ): ListAttributeNotation? {
        return documentNotation
                .objects
                .values[NotationConventions.mainObjectPath]
                ?.attributes
                ?.values
                ?.get(QueryDocument.verticesAttributeName)
                as? ListAttributeNotation
                ?: ListAttributeNotation(persistentListOf())
    }


    private fun vertexInfoLayers(
            verticesNotation: ListAttributeNotation
    ): VertexMatrix {
//        val documentNotation = documentNotation()
//        val vertexNotations = vertexNotations(documentNotation!!)!!
        val notation = state.graphStructure!!.graphNotation

        val vertexInfos = verticesNotation.values.withIndex().map {
            val vertexReference = ObjectReference.parse((it.value as ScalarAttributeNotation).value)
            val objectLocation = notation.coalesce.locate(vertexReference)
            val objectNotation = notation.coalesce.get(objectLocation)!!
            VertexInfo.fromDataflowNotation(
                    it.index,
                    objectLocation,
                    objectNotation)
        }

        return VertexMatrix.ofUnorderedInfos(vertexInfos)
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

        val verticesNotation = verticesNotation(documentNotation)
                ?: return

        val objectNotation = ObjectNotation
                .ofParent(archetypeLocation.objectPath.name)
                .upsertAttribute(VertexInfo.rowAttributeName, ScalarAttributeNotation(row.toString()))
                .upsertAttribute(VertexInfo.columnAttributeName, ScalarAttributeNotation(column.toString()))

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
                verticesNotation(documentNotation)?.let {
                    vertexInfoLayers(it)
                }

        if (vertexInfos?.isEmpty() != false) {
            styledH3 {
                css {
                    paddingTop = 1.em
                }

//                +"Empty query, please add flows using toolbar (above)"
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
            vertexMatrix: VertexMatrix
    ) {
        table {
            tbody {
                for (row in 0 .. vertexMatrix.usedRows) {
                    tr {
                        for (column in 0 .. vertexMatrix.usedColumns) {
                            styledTd {
//                                css {
//                                    padding(1.em)
//                                }

                                val vertexInfo = vertexMatrix.get(row, column)
                                if (vertexInfo == null) {
                                    absentVertex(row, column)
                                }
                                else {
                                    vertex(vertexInfo,
                                            graphStructure,
                                            visualDataflowModel)
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
            vertexInfo: VertexInfo,
            graphStructure: GraphStructure,
            visualDataflowModel: VisualDataflowModel
    ) {
        child(VertexController::class) {
            key = vertexInfo.objectLocation.toReference().asString()

            attrs {
                attributeNesting = AttributeNesting(persistentListOf(
                        AttributeSegment.ofIndex(vertexInfo.indexInVertices)))

                this.objectLocation = vertexInfo.objectLocation
                this.graphStructure = graphStructure

                this.visualVertexModel = visualDataflowModel.vertices[vertexInfo.objectLocation]
                        ?: VisualVertexModel.empty
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