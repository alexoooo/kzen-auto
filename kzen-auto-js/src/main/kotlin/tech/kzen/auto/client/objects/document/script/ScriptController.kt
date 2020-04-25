package tech.kzen.auto.client.objects.document.script

import kotlinx.coroutines.delay
import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayProps
import tech.kzen.auto.client.objects.ribbon.RibbonRun
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.DeletedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.collect.persistentListOf


class ScriptController:
        RPureComponent<ScriptController.Props, ScriptController.State>(),
//        NavigationGlobal.Observer,
//        LocalGraphStore.Observer,
//        ExecutionRepository.Observer,
        SessionGlobal.Observer,
        InsertionGlobal.Subscriber
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
            var stepController: StepController.Wrapper,
            var scriptCommander: ScriptCommander
    ): RProps


    class State(
//            var documentPath: DocumentPath?,
//            var graphStructure: GraphStructure?,
//            var imperativeModel: ImperativeModel?,
            var clientState: SessionState?,

            var creating: Boolean//,
//            var runningHost: DocumentPath?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            private val archetype: ObjectLocation,
            private val stepController: StepController.Wrapper,
            private val scriptCommander: ScriptCommander
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
                    this.scriptCommander = this@Wrapper.scriptCommander
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        documentPath = null
//        graphStructure = null
//        imperativeModel = null
        clientState = null
        creating = false
    }


    override fun componentDidMount() {
//        console.log("^^^^^^ script - componentDidMount")


        ClientContext.sessionGlobal.observe(this)

//        ClientContext.navigationGlobal.observe(this)
//
////        println("ProjectController - Subscribed")
//        async {
////            console.log("^^^^^^ script - adding observers")
//            delay(1)
//            ClientContext.mirroredGraphStore.observe(this)
////            delay(1)
//            ClientContext.executionRepository.observe(this)
////            delay(1)
            ClientContext.insertionGlobal.subscribe(this)
//        }
    }


    override fun componentWillUnmount() {
//        console.log("^^^^^^ script - componentWillUnmount")

//        println("ProjectController - Un-subscribed")
//        ClientContext.mirroredGraphStore.unobserve(this)
//        ClientContext.executionRepository.unobserve(this)
        ClientContext.insertionGlobal.unsubscribe(this)
//        ClientContext.navigationGlobal.unobserve(this)

        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
        val clientState = state.clientState
                ?: return

//        if (clientState.activeHost() == null &&
//                clientState.navigationRoute.documentPath != null)
//        {
//            ClientContext.navigationGlobal.parameterize(RequestParams(
//                    mapOf(RibbonRun.runningKey to listOf(clientState.navigationRoute.documentPath.asString()))
//            ))
//        }

//        if (state.graphStructure == null) {
////            console.log("~~~ not ready - componentDidUpdate")
//            return
//        }
//        console.log("%#$%#$%#$ componentDidUpdate",
//                state.imperativeModel?.frames?.map { it.path.asString() })

//        val runningHost = clientState.activeHost()
//        if ((prevState.runningHost == null || prevState.graphStructure == null) &&
//                runningHost != null &&
//                state.imperativeModel == null)
//        {
//            val graphStructure = state.graphStructure!!
//            async {
//                ClientContext.executionRepository.executionModel(
//                        runningHost, graphStructure)
//            }
//            return
//        }
//
////        console.log("%#$%#$%#$ componentDidUpdate", state.documentPath, prevState.documentPath)
//        if (state.documentPath != null &&
//                state.documentPath != prevState.documentPath &&
//                state.imperativeModel?.frames?.find { it.path == state.documentPath} == null)
//        {
//            async {
//                val executionModel = ClientContext.executionRepository.executionModel(
//                        state.documentPath!!,
//                        state.graphStructure!!)
//
//                setState {
//                    imperativeModel = executionModel
//                }
//            }
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
        setState {
            this.clientState = clientState
        }
    }

//    override fun handleNavigation(
//            documentPath: DocumentPath?,
//            parameters: RequestParams
//    ) {
////        console.log("^^^^^^ script - handleNavigation", documentPath, parameters)
//
//        setState {
//            this.documentPath = documentPath
//
//            runningHost = parameters.get(RibbonRun.runningKey)?.let { DocumentPath.parse(it) }
//        }
//    }


//    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
//        if ((event is DeletedDocumentEvent || event is RenamedDocumentRefactorEvent) &&
//                event.documentPath == state.documentPath) {
//            return
//        }
//
//        setState {
//            this.graphStructure = graphDefinition.successful.graphStructure
//        }
//    }


//    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}
//
//
//    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
////        console.log("^^^^^ onStoreRefresh")
//        setState {
//            this.graphStructure = graphDefinition.successful.graphStructure
//        }
//    }


//    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {}


//    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel) {
////        console.log("^^^^ onExecutionModel: " +
////                "$host - ${state.documentPath} - ${state.runningHost} - $executionModel")
//
////        if (state.documentPath != host &&
////                executionModel.frames.find { it.path == state.documentPath} == null)
//        if (state.runningHost != null && state.runningHost != host ||
//                state.runningHost == null &&
//                state.documentPath != host &&
//                executionModel.frames.find { it.path == state.documentPath} == null)
//        {
//            return
//        }
//
////        console.log("Assign exec model")
//
//        setState {
//            imperativeModel = executionModel
//        }
//    }


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
        val clientState = state.clientState!!

        val archetypeObjectLocation = ClientContext.insertionGlobal
                .getAndClearSelection()
                ?: return

        val documentPath = clientState.navigationRoute.documentPath!!
        val containingObjectLocation = ObjectLocation(
                documentPath, NotationConventions.mainObjectPath)

        val commands = props.scriptCommander.createCommands(
                containingObjectLocation,
                ScriptDocument.stepsAttributePath,
                index,
                archetypeObjectLocation,
                clientState.graphDefinitionAttempt.successful.graphStructure
        )

        async {
            for (command in commands) {
                ClientContext.mirroredGraphStore.apply(command)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val clientState = state.clientState
                ?: return

//        val structure = state.graphStructure
//                ?: return
//
//        val documentPath: DocumentPath = state.documentPath
//                ?: return

        val documentPath = clientState.navigationRoute.documentPath
                ?: return

        val imperativeModel = clientState.imperativeModel
                ?: ClientContext.executionRepository.emptyState(
                        documentPath, clientState.graphDefinitionAttempt.successful.graphStructure)

        styledDiv {
            css {
                marginLeft = 2.em
            }

            steps(clientState, imperativeModel)
        }

        runController(clientState, imperativeModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.steps(
            clientState: SessionState,
            imperativeModel: ImperativeModel
    ) {
        val graphStructure: GraphStructure = clientState.graphDefinitionAttempt.successful.graphStructure
        val documentPath: DocumentPath = clientState.navigationRoute.documentPath!!

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
                nonEmptySteps(graphStructure, documentPath, stepLocations, clientState, imperativeModel)
            }
        }
    }


    private fun RBuilder.nonEmptySteps(
            graphStructure: GraphStructure,
            documentPath: DocumentPath,
            stepLocations: List<ObjectLocation>,
            clientState: SessionState,
            imperativeModel: ImperativeModel
    ) {
//        +"nonEmptySteps: $stepLocations"
//        +"imperativeModel: running ${imperativeModel.running}"

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
                        imperativeModel,
                        stepLocations.size)

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
            imperativeModel: ImperativeModel,
            stepCount: Int
    ) {
        val active =
                state.clientState?.activeHost != null &&
                imperativeModel.frames.any { it.path == state.clientState?.navigationRoute?.documentPath }

        span {
            key = objectLocation.toReference().asString()

            props.stepController.child(this) {
                attrs {
                    common = StepDisplayProps.Common(
                            graphStructure,
                            objectLocation,
                            AttributeNesting(persistentListOf(AttributeSegment.ofIndex(index))),
                            imperativeModel,
                            first = index == 0,
                            last = index == stepCount - 1,
                            active = active)
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.runController(
            clientState: SessionState,
            imperativeModel: ImperativeModel
    ) {
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
                    documentPath = clientState.navigationRoute.documentPath!!
                    runningHost = clientState.activeHost
                    structure = clientState.graphDefinitionAttempt.successful.graphStructure
                    execution = imperativeModel
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