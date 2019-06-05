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
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowManager
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.InsertListItemInAttributeCommand
import tech.kzen.lib.common.structure.notation.edit.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.model.*
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.persistentMapOf


@Suppress("unused")
class QueryController:
        RPureComponent<RProps, QueryController.State>(),
        GraphStructureManager.Observer,
        InsertionManager.Observer,
        NavigationManager.Observer,
        VisualDataflowManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val edgePipeName = "EdgePipe"
    }


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

        val documentPath = state.documentPath
        if (documentPath != prevState.documentPath) {
//            console.log("ProjectController componentDidUpdate", state.documentPath, prevState.documentPath)

            if (documentPath == null) {
                setState {
                    visualDataflowModel = null
                }
            }
            else {
                async {
                    val visualDataflowModel = ClientContext.visualDataflowManager.get(documentPath)
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
        val documentNotation = documentNotation()
                ?: return

        val archetypeLocation = ClientContext.insertionManager.getAndClearSelection()
                ?: return

        val archetypeNotation = state.graphStructure!!.graphNotation.coalesce[archetypeLocation]!!
        val archetypeIs = archetypeNotation.get(NotationConventions.isAttributeName)?.asString()!!

        val containingObjectLocation = ObjectLocation(
                state.documentPath!!, NotationConventions.mainObjectPath)

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
                            QueryDocument.edgesAttributePath,
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
                            QueryDocument.verticesAttributePath,
                            PositionIndex(verticesNotation.values.size),
                            NameConventions.randomAnonymous(),
                            PositionIndex(documentNotation.objects.values.size),
                            objectNotation
                    )
                }

        async {
            ClientContext.commandBus.apply(command)
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
                state.graphStructure!!.graphNotation,  verticesNotation, edgesNotation)

        if (dataflowMatrix.isEmpty()) {
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
                        dataflowMatrix)
            }
        }
    }


    private fun RBuilder.nonEmptyDag(
            graphStructure: GraphStructure,
            visualDataflowModel: VisualDataflowModel,
            dataflowMatrix: DataflowMatrix
    ) {
        val dataflowDag = DataflowDag.of(dataflowMatrix)

        table {
            tbody {
                for (row in 0 .. dataflowMatrix.usedRows) {
                    tr {
                        for (column in 0 .. dataflowMatrix.usedColumns) {
                            styledTd {
                                css {
                                    padding(1.em)
//                                    borderStyle = BorderStyle.solid
//                                    borderColor = Color.black
//                                    borderWidth = 1.px
                                }
//                                +"[$row, $column]"

                                val cellDescriptor = dataflowMatrix.get(row, column)

                                if (cellDescriptor == null) {
                                    key = "absent-$row-$column"
                                    absentCell(row, column)
                                }
                                else {
                                    key = cellDescriptor.key()
                                    cell(cellDescriptor,
                                            graphStructure,
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


    private fun RBuilder.cell(
            cellDescriptor: CellDescriptor,
            graphStructure: GraphStructure,
            visualDataflowModel: VisualDataflowModel,
            dataflowMatrix: DataflowMatrix,
            dataflowDag: DataflowDag
    ) {
        child(CellController::class) {
            attrs {
                this.cellDescriptor = cellDescriptor

                attributeNesting = AttributeNesting(persistentListOf(
                        AttributeSegment.ofIndex(cellDescriptor.indexInContainer)))

                documentPath = state.documentPath!!

                this.graphStructure = graphStructure
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

            child(QueryRunController::class) {
                attrs {
                    documentPath = state.documentPath
                    graphStructure = state.graphStructure
                    visualDataflowModel = state.visualDataflowModel
                }
            }
        }
    }
}