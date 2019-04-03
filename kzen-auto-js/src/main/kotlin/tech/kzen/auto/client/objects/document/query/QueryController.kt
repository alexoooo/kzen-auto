package tech.kzen.auto.client.objects.document.query

import kotlinx.css.Cursor
import kotlinx.css.em
import kotlinx.html.title
import react.*
import styled.css
import styled.styledDiv
import styled.styledH3
import styled.styledSpan
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.NameConventions
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.QueryDocument
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex


class QueryController:
        RComponent<RProps, QueryController.State>(),
        ModelManager.Observer,
//        ExecutionManager.Observer,
        InsertionManager.Observer,
        NavigationManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var documentPath: DocumentPath?,
            var structure: GraphStructure?,
//            var execution: ExecutionModel?,
            var creating: Boolean
    ) : RState


    @Suppress("unused")
    class Wrapper(
            private val type: DocumentArchetype
    ) :
            DocumentController {
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
            prevState: QueryController.State,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(projectStructure: GraphStructure, event: NotationEvent?) {
        setState {
            structure = projectStructure
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
    private fun onCreate(index: Int) {
        val objectNotation = ClientContext.insertionManager
                .getAndClearSelection()
                ?.let { ObjectNotation.ofParent(it.toReference().name) }
                ?: return

        val containingObjectLocation = ObjectLocation(
                state.documentPath!!, NotationConventions.mainObjectPath)

        // NB: +1 offset for main Script object
        val positionInDocument = PositionIndex(index + 1)

        val command = InsertObjectInListAttributeCommand(
                containingObjectLocation,
                QueryDocument.sourcesAttributePath,
                PositionIndex(index),
                NameConventions.randomAnonymous(),
                positionInDocument,
                objectNotation
        )

        async {
            ClientContext.commandBus.apply(command)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val graphStructure = state.structure
                ?: return

        val documentPath = state.documentPath
                ?: return

//        val documentNotation: DocumentNotation =
//                graphStructure.graphNotation.documents.values[documentPath]
//                ?: return

        val mainObjectLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)

        val sourcesNotation = graphStructure
                .graphNotation
                .transitiveAttribute(mainObjectLocation, QueryDocument.sourcesAttributePath)
                as? ListAttributeNotation
                ?: return

        val sourceReferences = sourcesNotation.values.map { ObjectReference.parse(it.asString()!!) }

        if (sourceReferences.isEmpty()) {
            styledH3 {
                css {
                    paddingTop = 1.em
                }

                +"Empty query, please add flows using toolbar (above)"
            }

            insertionPoint(0)
        }
        else {
            nonEmptySources(graphStructure, /*documentNotation,*/ documentPath, sourceReferences)
        }
    }


    private fun RBuilder.nonEmptySources(
            graphStructure: GraphStructure,
            documentPath: DocumentPath,
            sourceReferences: List<ObjectReference>
    ) {
        insertionPoint(0)


        styledDiv {
            css {
                width = 20.em
            }

            for ((index, sourceReference) in sourceReferences.withIndex()) {
                val sourceLocation = graphStructure.graphNotation.coalesce.locate(sourceReference)
//                val objectPath = sourceLocation.objectPath
//
//                val keyLocation = ObjectLocation(documentPath, objectPath)

                source(index,
                        sourceLocation,
                        graphStructure)

                if (index < sourceReferences.size - 1) {
                    insertionPoint(index + 1)
                }
            }
        }

        insertionPoint(sourceReferences.size)
    }


    private fun RBuilder.insertionPoint(index: Int) {
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
                        onCreate(index)
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }
        }
    }


    private fun RBuilder.source(
            index: Int,
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure
    ) {
        child(SourceController::class) {
            attrs {
                attributeNesting = AttributeNesting(listOf(AttributeSegment.ofIndex(index)))
                this.objectLocation = objectLocation
                this.graphStructure = graphStructure
            }
        }
    }
}