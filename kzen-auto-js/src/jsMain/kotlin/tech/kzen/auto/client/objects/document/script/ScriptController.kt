package tech.kzen.auto.client.objects.document.script
//
//import emotion.react.css
//import js.core.jso
//import mui.material.IconButton
//import react.ChildrenBuilder
//import react.Props
//import react.State
//import react.dom.html.ReactHTML.div
//import react.dom.html.ReactHTML.span
//import react.react
//import tech.kzen.auto.client.api.ReactWrapper
//import tech.kzen.auto.client.objects.document.DocumentController
//import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
//import tech.kzen.auto.client.objects.document.script.step.StepController
//import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayPropsCommon
//import tech.kzen.auto.client.objects.ribbon.RibbonController
//import tech.kzen.auto.client.service.ClientContext
//import tech.kzen.auto.client.service.global.InsertionGlobal
//import tech.kzen.auto.client.service.global.SessionGlobal
//import tech.kzen.auto.client.service.global.SessionState
//import tech.kzen.auto.client.util.async
//import tech.kzen.auto.client.wrap.RPureComponent
//import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
//import tech.kzen.auto.client.wrap.material.ArrowDownwardIcon
//import tech.kzen.auto.client.wrap.setState
//import tech.kzen.auto.common.objects.document.script.ScriptDocument
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
//import tech.kzen.lib.common.model.attribute.AttributeNesting
//import tech.kzen.lib.common.model.attribute.AttributeSegment
//import tech.kzen.lib.common.model.document.DocumentPath
//import tech.kzen.lib.common.model.location.ObjectLocation
//import tech.kzen.lib.common.model.location.ObjectReference
//import tech.kzen.lib.common.model.location.ObjectReferenceHost
//import tech.kzen.lib.common.model.structure.GraphStructure
//import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
//import tech.kzen.lib.common.reflect.Reflect
//import tech.kzen.lib.common.service.notation.NotationConventions
//import tech.kzen.lib.platform.collect.persistentListOf
//import web.cssom.*
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface ScriptControllerProps: Props {
//    var stepController: StepController.Wrapper
//    var scriptCommander: ScriptCommander
//}
//
//
//external interface ScriptControllerState: State {
//    var clientState: SessionState?
//    var creating: Boolean
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class ScriptController:
//    RPureComponent<ScriptControllerProps, ScriptControllerState>(),
//    SessionGlobal.Observer,
//    InsertionGlobal.Subscriber
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        fun stepLocations(
//            graphStructure: GraphStructure,
//            documentPath: DocumentPath
//        ): List<ObjectLocation>? {
//            val mainObjectLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
//
//            val stepsNotation = graphStructure
//                    .graphNotation
//                    .firstAttribute(mainObjectLocation, ScriptDocument.stepsAttributePath)
//                    as? ListAttributeNotation
//                    ?: return null
//
//            val objectReferenceHost = ObjectReferenceHost.ofLocation(mainObjectLocation)
//
//            return stepsNotation
//                    .values
//                    .map { ObjectReference.parse(it.asString()!!) }
//                    .map { graphStructure.graphNotation.coalesce.locate(it, objectReferenceHost) }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    @Reflect
//    class Wrapper(
//        private val archetype: ObjectLocation,
//        private val stepController: StepController.Wrapper,
//        private val scriptCommander: ScriptCommander,
//        private val ribbonController: RibbonController.Wrapper
//    ):
//        DocumentController
//    {
//        override fun archetypeLocation(): ObjectLocation {
//            return archetype
//        }
//
//
//        override fun header(): ReactWrapper<Props> {
//            return object: ReactWrapper<Props> {
//                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
//                    ribbonController.child(this) {}
//                }
//            }
//        }
//
//
//        override fun body(): ReactWrapper<Props> {
//            return object: ReactWrapper<Props> {
//                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
//                    ScriptController::class.react {
//                        this.stepController = this@Wrapper.stepController
//                        this.scriptCommander = this@Wrapper.scriptCommander
//
//                        block()
//                    }
//                }
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun ScriptControllerState.init(props: ScriptControllerProps) {
//        clientState = null
//        creating = false
//    }
//
//
//    override fun componentDidMount() {
//        ClientContext.sessionGlobal.observe(this)
//        ClientContext.insertionGlobal.subscribe(this)
//    }
//
//
//    override fun componentWillUnmount() {
//        ClientContext.insertionGlobal.unsubscribe(this)
//        ClientContext.sessionGlobal.unobserve(this)
//    }
//
//
//    override fun componentDidUpdate(
//            prevProps: ScriptControllerProps,
//            prevState: ScriptControllerState,
//            snapshot: Any
//    ) {
////        val clientState = state.clientState
////                ?: return
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun onClientState(clientState: SessionState) {
////        console.log("#!#@!#@! onClientState - ${clientState.imperativeModel}")
//        setState {
//            this.clientState = clientState
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun onInsertionSelected(action: ObjectLocation) {
//        setState {
//            creating = true
//        }
//    }
//
//
//    override fun onInsertionUnselected() {
//        setState {
//            creating = false
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onCreate(index: Int) {
//        val clientState = state.clientState!!
//
//        val archetypeObjectLocation = ClientContext.insertionGlobal
//                .getAndClearSelection()
//                ?: return
//
//        val documentPath = clientState.navigationRoute.documentPath!!
//        val containingObjectLocation = ObjectLocation(
//                documentPath, NotationConventions.mainObjectPath)
//
//        val commands = props.scriptCommander.createCommands(
//                containingObjectLocation,
//                ScriptDocument.stepsAttributePath,
//                index,
//                archetypeObjectLocation,
//                clientState.graphDefinitionAttempt.graphStructure
//        )
//
//        async {
//            for (command in commands) {
//                ClientContext.mirroredGraphStore.apply(command)
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun ChildrenBuilder.render() {
//        val clientState = state.clientState
//            ?: return
//
//        val documentPath = clientState.navigationRoute.documentPath
//            ?: return
//
//        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
//            ?: return
//
//        if (! ScriptDocument.isScript(documentNotation)) {
//            return
//        }
//
//        val imperativeModel = clientState.imperativeModel
//                ?: ClientContext.executionRepository.emptyState(
//                        documentPath, clientState.graphDefinitionAttempt.graphStructure)
//
//        div {
//            css {
//                marginLeft = 2.em
//            }
//
//            steps(clientState, imperativeModel)
//        }
//
//        runController(clientState, imperativeModel)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun ChildrenBuilder.steps(
//            clientState: SessionState,
//            imperativeModel: ImperativeModel
//    ) {
//        val graphStructure: GraphStructure = clientState.graphDefinitionAttempt.graphStructure
//        val documentPath: DocumentPath = clientState.navigationRoute.documentPath!!
//
//        val stepLocations = stepLocations(graphStructure, documentPath)
//                ?: return
//
//        if (stepLocations.isEmpty()) {
//            div {
//                css {
//                    paddingTop = 2.em
//                }
//
//                div {
//                    css {
//                        fontSize = 1.5.em
//                    }
//                    +"Empty script, please add steps from the toolbar (above)"
//                }
//
//                insertionPoint(0)
//            }
//        }
//        else {
//            div {
//                css {
//                    paddingLeft = 1.em
//                }
//                nonEmptySteps(documentPath, stepLocations, imperativeModel)
//            }
//        }
//    }
//
//
//    private fun ChildrenBuilder.nonEmptySteps(
//            documentPath: DocumentPath,
//            stepLocations: List<ObjectLocation>,
//            imperativeModel: ImperativeModel
//    ) {
////        +"nonEmptySteps: $stepLocations"
////        +"imperativeModel: running ${imperativeModel.running}"
//
//        insertionPoint(0)
//
//        div {
//            css {
//                width = StepController.width
//            }
//
//            for ((index, stepLocation) in stepLocations.withIndex()) {
//                val objectPath = stepLocation.objectPath
//
//                val keyLocation = ObjectLocation(documentPath, objectPath)
//
//                renderStep(
//                        index,
//                        keyLocation,
//                        imperativeModel,
//                        stepLocations.size)
//
//                if (index < stepLocations.size - 1) {
//                    downArrowWithInsertionPoint(index + 1)
//                }
//            }
//        }
//
//        insertionPoint(stepLocations.size)
//    }
//
//
//    private fun ChildrenBuilder.downArrowWithInsertionPoint(index: Int) {
//        div {
//            css {
//                position = Position.relative
//                height = 4.em
//                width = StepController.width.div(2).minus(1.em)
//            }
//
//            div {
//                css {
//                    marginTop = 0.5.em
//
//                    position = Position.absolute
//                    height = 1.em
//                    width = 1.em
//                    top = 0.em
//                    left = 0.em
//                }
//                insertionPoint(index)
//            }
//
//            div {
//                css {
//                    position = Position.absolute
//                    height = 3.em
//                    width = 3.em
//                    top = 0.em
//                    left = StepController.width.div(2).minus(1.5.em)
//
//                    marginTop =  0.5.em
//                    marginBottom = 0.5.em
//                }
//
//                ArrowDownwardIcon::class.react {
//                    style = jso {
//                        fontSize = 3.em
//                    }
//                }
//            }
//        }
//    }
//
//
//    private fun ChildrenBuilder.insertionPoint(index: Int) {
//        span {
//            if (state.creating) {
//                title = "Insert action here"
//            }
//
//            IconButton {
//                css {
//                    if (! state.creating) {
//                        opacity = number(0.0)
//                        cursor = Cursor.default
//                    }
//                }
//
//                onClick = {
//                    onCreate(index)
//                }
//
//                AddCircleOutlineIcon::class.react {}
//            }
//        }
//    }
//
//
//    private fun ChildrenBuilder.renderStep(
//            index: Int,
//            objectLocation: ObjectLocation,
//            imperativeModel: ImperativeModel,
//            stepCount: Int
//    ) {
//        span {
//            key = objectLocation.toReference().asString()
//
//            props.stepController.child(this) {
//                common = StepDisplayPropsCommon(
//                    state.clientState!!,
//                    objectLocation,
//                    AttributeNesting(persistentListOf(AttributeSegment.ofIndex(index))),
//                    imperativeModel,
//                    first = index == 0,
//                    last = index == stepCount - 1)
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun ChildrenBuilder.runController(
//            clientState: SessionState,
//            imperativeModel: ImperativeModel
//    ) {
//        div {
//            css {
//                position = Position.fixed
//                bottom = 0.px
//                right = 0.px
//                marginRight = 2.em
//                marginBottom = 2.em
//            }
//
//            ScriptRunController::class.react {
//                documentPath = clientState.navigationRoute.documentPath!!
//                runningHost = clientState.activeHost
//                structure = clientState.graphDefinitionAttempt.graphStructure
//                execution = imperativeModel
//            }
//        }
//    }
//}