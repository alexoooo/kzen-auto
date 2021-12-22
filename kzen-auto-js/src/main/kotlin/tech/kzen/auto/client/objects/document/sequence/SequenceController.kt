package tech.kzen.auto.client.objects.document.sequence

import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.attrs
import react.dom.html.ReactHTML.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.run.ExecutionRunController
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.sequence.model.SequenceState
import tech.kzen.auto.client.objects.document.sequence.model.SequenceStore
import tech.kzen.auto.client.objects.document.sequence.step.SequenceStepController
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayProps
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.ArrowDownwardIcon
import tech.kzen.auto.client.wrap.material.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.persistentListOf


class SequenceController:
    RPureComponent<SequenceController.Props, SequenceController.State>(),
    SequenceStore.Observer,
    InsertionGlobal.Subscriber,
    SessionGlobal.Observer
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
                    .firstAttribute(mainObjectLocation, SequenceConventions.stepsAttributePath)
                    as? ListAttributeNotation
                    ?: return null

            val objectReferenceHost = ObjectReferenceHost.ofLocation(mainObjectLocation)

            return stepsNotation
                    .values
                    .map { ObjectReference.parse(it.asString()!!) }
                    .map { graphStructure.graphNotation.coalesce.locate(it, objectReferenceHost) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var stepController: SequenceStepController.Wrapper
    }


    interface State: react.State {
        var clientState: SessionState?
        var sequenceState: SequenceState?
        var creating: Boolean
    }


    override fun State.init(props: Props) {
        clientState = null
        sequenceState = null
        creating = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = SequenceStore()


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        private val stepController: SequenceStepController.Wrapper,
//            private val scriptCommander: ScriptCommander
        private val ribbonController: RibbonController.Wrapper
    ):
            DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<react.Props> {
            return object: ReactWrapper<react.Props> {
                override fun child(input: RBuilder, handler: RHandler<react.Props>) {
                    ribbonController.child(input) {}
                }
            }
        }


        override fun body(): ReactWrapper<react.Props> {
            return object: ReactWrapper<react.Props> {
                override fun child(input: RBuilder, handler: RHandler<react.Props>) {
                    input.child(SequenceController::class) {
                        attrs {
                            this.stepController = this@Wrapper.stepController
//                    this.scriptCommander = this@Wrapper.scriptCommander
                        }

                        handler()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        store.didMount(this)
        ClientContext.sessionGlobal.observe(this)
        ClientContext.insertionGlobal.subscribe(this)
    }


    override fun componentWillUnmount() {
        store.willUnmount()
        ClientContext.insertionGlobal.unsubscribe(this)
        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
//        val clientState = state.clientState
//                ?: return
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onSequenceState(sequenceState: SequenceState) {
        setState {
            this.sequenceState = sequenceState
        }
    }


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
    override fun onClientState(clientState: SessionState) {
//        console.log("#!#@!#@! onClientState - ${clientState.imperativeModel}")
        setState {
            this.clientState = clientState
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

//        val commands = props.scriptCommander.createCommands(
//                containingObjectLocation,
//                ScriptDocument.stepsAttributePath,
//                index,
//                archetypeObjectLocation,
//                clientState.graphDefinitionAttempt.graphStructure
//        )

//        async {
//            for (command in commands) {
//                ClientContext.mirroredGraphStore.apply(command)
//            }
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        styledDiv {
//            css {
//                margin(2.em)
//            }
//
//            +"Foo"
//
//            if (state.creating) {
//                +"[_]"
//            }
//        }

        val clientState = state.clientState
            ?: return

        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (! SequenceState.isSequence(documentNotation)) {
            return
        }

//        val imperativeModel = clientState.imperativeModel
//                ?: ClientContext.executionRepository.emptyState(
//                        documentPath, clientState.graphDefinitionAttempt.graphStructure)
//
        styledDiv {
            css {
                marginLeft = 2.em
            }

            steps(clientState/*, imperativeModel*/)
        }

        runController(clientState/*, imperativeModel*/)
    }

    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.steps(
            clientState: SessionState,
//            imperativeModel: ImperativeModel
    ) {
        val graphStructure: GraphStructure = clientState.graphDefinitionAttempt.graphStructure
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
                nonEmptySteps(documentPath, stepLocations/*, imperativeModel*/)
            }
        }
    }


    private fun RBuilder.nonEmptySteps(
            documentPath: DocumentPath,
            stepLocations: List<ObjectLocation>,
//            imperativeModel: ImperativeModel
    ) {
//        +"nonEmptySteps: $stepLocations"
//        +"imperativeModel: running ${imperativeModel.running}"

        insertionPoint(0)

        styledDiv {
            css {
                width = StepController.width
            }

            for ((index, stepLocation) in stepLocations.withIndex()) {
                val objectPath = stepLocation.objectPath

                val keyLocation = ObjectLocation(documentPath, objectPath)

                renderStep(
                        index,
                        keyLocation,
//                        imperativeModel,
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
                width = StepController.width.div(2).minus(1.em)
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
                    left = StepController.width.div(2).minus(1.5.em)

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
//            imperativeModel: ImperativeModel,
            stepCount: Int
    ) {
        span {
            key = objectLocation.toReference().asString()

            props.stepController.child(this) {
                attrs {
                    common = SequenceStepDisplayProps.Common(
                        state.clientState!!,
                        objectLocation,
                        AttributeNesting(persistentListOf(AttributeSegment.ofIndex(index))),
//                        imperativeModel,
                        first = index == 0,
                        last = index == stepCount - 1)
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.runController(
            clientState: SessionState,
//            imperativeModel: ImperativeModel
    ) {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            val runSate = store.state().run
            child(ExecutionRunController::class) {
                attrs {
                    thisRunning = runSate.logicStatus?.active != null
                    thisSubmitting = runSate.submitting()
                    otherRunning = runSate.otherRunning

//                    outputTerminal = (reportState.output.outputInfo?.status ?: OutputStatus.Missing).isTerminal()
                    outputTerminal = false

//                    reportStore = store
                    executionRunStore = store.run
                }
            }
        }
    }
}