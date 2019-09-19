package tech.kzen.auto.client.objects.document.script

import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.collect.persistentListOf


@Suppress("unused")
class ScriptController:
        RPureComponent<ScriptController.Props, ScriptController.State>(),
        NavigationManager.Observer,
        LocalGraphStore.Observer,
        ExecutionManager.Observer,
        InsertionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun stepLocations(
                graphStructure: GraphStructure,
                documentPath: DocumentPath
        ): List<ObjectLocation>? {
            val mainObjectLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)

            val stepsNotation = graphStructure
                    .graphNotation
                    .transitiveAttribute(mainObjectLocation, ScriptDocument.stepsAttributePath)
                    as? ListAttributeNotation
                    ?: return null

            val objectReferenceHost = ObjectReferenceHost.ofLocation(mainObjectLocation)

            return stepsNotation
                    .values
                    .map { ObjectReference.parse(it.asString()!!) }
                    .map { graphStructure.graphNotation.coalesce.locate(it, objectReferenceHost) }
        }
    }


    class Props(
            var stepController: StepController.Wrapper
    ): RProps


    class State(
            var documentPath: DocumentPath?,
            var graphStructure: GraphStructure?,
            var imperativeModel: ImperativeModel?,
            var creating: Boolean
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val archetype: ObjectLocation,
            private val stepController: StepController.Wrapper
    ):
            DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(ScriptController::class) {
                attrs {
                    this.stepController = this@Wrapper.stepController
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        documentPath = null
        graphStructure = null
        imperativeModel = null
        creating = false
    }


    override fun componentDidMount() {
//        console.log("^^^^^^ script - componentDidMount")

//        println("ProjectController - Subscribed")
        async {
//            console.log("^^^^^^ script - adding observers")
            ClientContext.mirroredGraphStore.observe(this)
            ClientContext.executionManager.observe(this)
            ClientContext.insertionManager.subscribe(this)
            ClientContext.navigationManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        console.log("^^^^^^ script - componentWillUnmount")

//        println("ProjectController - Un-subscribed")
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.executionManager.unobserve(this)
        ClientContext.insertionManager.unSubscribe(this)
        ClientContext.navigationManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
//        console.log("%#$%#$%#$ componentDidUpdate", state.documentPath, prevState.documentPath)
        if (state.documentPath != null && state.documentPath != prevState.documentPath) {
            async {
                val executionModel = ClientContext.executionManager.executionModel(state.documentPath!!)
//                console.log("%#$%#$%#$ componentDidUpdate",
//                        state.documentPath,
//                        prevState.documentPath,
//                        executionModel)

                setState {
                    imperativeModel = executionModel
                }
            }
        }

//        console.log("ProjectController componentDidUpdate", state, prevState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?) {
//        console.log("^^^^^^ script - handleNavigation", documentPath)

        setState {
            this.documentPath = documentPath
        }
    }


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        setState {
            this.graphStructure = graphDefinition.successful.graphStructure
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        setState {
            this.graphStructure = graphDefinition.successful.graphStructure
        }
    }



    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel) {
        if (state.documentPath != host) {
            return
        }

//        console.log("^^^^ onExecutionModel: $host - ${state.documentPath} - $executionModel")
        setState {
            imperativeModel = executionModel
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
    private fun onCreate(index: Int) {
        val archetypeObjectLocation = ClientContext.insertionManager
                .getAndClearSelection()
                ?: return

        val documentPath = state.documentPath!!
        val containingObjectLocation = ObjectLocation(
                documentPath, NotationConventions.mainObjectPath)

        val command = ScriptDocument.createCommand(
                containingObjectLocation,
                ScriptDocument.stepsAttributePath,
                index,
                archetypeObjectLocation,
                state.graphStructure!!
        )

        async {
            ClientContext.mirroredGraphStore.apply(command)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val structure = state.graphStructure
                ?: return

        val documentPath: DocumentPath = state.documentPath
                ?: return

        styledDiv {
            css {
                marginLeft = 2.em
            }

            steps(structure, documentPath)
        }

        runController()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.steps(
            graphStructure: GraphStructure,
            documentPath: DocumentPath
    ) {
        val stepLocations = stepLocations(graphStructure, documentPath)
                ?: return

        if (stepLocations.isEmpty()) {
            styledDiv {
                css {
                    paddingTop = 2.em
                }

                styledDiv {
                    css {
                        fontSize = 1.5.em
                    }
                    +"Empty script, please add steps from the toolbar (above)"
                }

                insertionPoint(0)
            }
        }
        else {
            styledDiv {
                css {
                    paddingLeft = 1.em
                }
                nonEmptySteps(graphStructure, documentPath, stepLocations)
            }
        }
    }


    private fun RBuilder.nonEmptySteps(
            graphStructure: GraphStructure,
            documentPath: DocumentPath,
            stepLocations: List<ObjectLocation>
    ) {
        val imperativeModel = state.imperativeModel
                ?: return

        insertionPoint(0)

        styledDiv {
            css {
                width = 20.em
            }

            for ((index, stepLocation) in stepLocations.withIndex()) {
                val objectPath = stepLocation.objectPath

                val keyLocation = ObjectLocation(documentPath, objectPath)

                renderStep(
                        index,
                        keyLocation,
                        graphStructure,
                        imperativeModel)

                if (index < stepLocations.size - 1) {
                    downArrowWithInsertionPoint(index + 1)
                }
            }
        }

        insertionPoint(stepLocations.size)
    }


    private fun RBuilder.downArrowWithInsertionPoint(index: Int) {
        styledDiv {
            css {
                position = Position.relative
                height = 4.em
                width = 9.em
            }

            styledDiv {
                css {
                    marginTop = 0.5.em

                    position = Position.absolute
                    height = 1.em
                    width = 1.em
                    top = 0.em
                    left = 0.em
                }
                insertionPoint(index)
            }

            styledDiv {
                css {
                    position = Position.absolute
                    height = 3.em
                    width = 3.em
                    top = 0.em
                    left = 8.5.em

                    marginTop =  0.5.em
                    marginBottom = 0.5.em
                }

                child(ArrowDownwardIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.insertionPoint(index: Int) {
        styledSpan {
            attrs {
                if (state.creating) {
                    title = "Insert action here"
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


    private fun RBuilder.renderStep(
            index: Int,
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            imperativeModel: ImperativeModel
    ) {
        span {
            key = objectLocation.toReference().asString()

            props.stepController.child(this) {
                attrs {
                    attributeNesting = AttributeNesting(persistentListOf(AttributeSegment.ofIndex(index)))
                    this.objectLocation = objectLocation
                    this.graphStructure = graphStructure
                    this.imperativeModel = imperativeModel
                }
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

            child(ScriptRunController::class) {
                attrs {
                    documentPath = state.documentPath
                    structure = state.graphStructure
                    execution = state.imperativeModel
                }
            }
        }
    }

//    private fun RBuilder.refresh() {
//        input (type = InputType.button) {
//            attrs {
//                value = "Reload"
//                onClickFunction = { onRefresh() }
//            }
//        }
//    }
}