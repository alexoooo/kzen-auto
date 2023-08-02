package tech.kzen.auto.client.objects.document.sequence

import emotion.react.css
import js.core.jso
import mui.material.IconButton
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.sequence.command.SequenceCommander
import tech.kzen.auto.client.objects.document.sequence.model.SequenceState
import tech.kzen.auto.client.objects.document.sequence.model.SequenceStore
import tech.kzen.auto.client.objects.document.sequence.progress.SequenceProgressController
import tech.kzen.auto.client.objects.document.sequence.step.StepDisplayManager
import tech.kzen.auto.client.objects.document.sequence.step.display.MultiStepDisplay
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayPropsCommon
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.ArrowDownwardIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
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
import web.cssom.*


//-----------------------------------------------------------------------------------------------------------------
external interface SequenceControllerProps: Props {
    var stepController: StepDisplayManager.Wrapper
    var sequenceCommander: SequenceCommander
}


external interface SequenceControllerState: State {
    var clientState: SessionState?
    var sequenceState: SequenceState?
    var creating: Boolean
}


//-----------------------------------------------------------------------------------------------------------------
class SequenceController:
    RPureComponent<SequenceControllerProps, SequenceControllerState>(),
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
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        private val stepController: StepDisplayManager.Wrapper,
        private val sequenceCommander: SequenceCommander,
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
                    SequenceController::class.react {
                        this.stepController = this@Wrapper.stepController
                        this.sequenceCommander = this@Wrapper.sequenceCommander
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = SequenceStore()


    //-----------------------------------------------------------------------------------------------------------------
    override fun SequenceControllerState.init(props: SequenceControllerProps) {
        clientState = null
        sequenceState = null
        creating = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        store.didMount()
        store.observe(this)
        ClientContext.sessionGlobal.observe(this)
        ClientContext.insertionGlobal.subscribe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionGlobal.unsubscribe(this)
        ClientContext.sessionGlobal.unobserve(this)
        store.unobserve(this)
        store.willUnmount()
    }


    override fun componentDidUpdate(
        prevProps: SequenceControllerProps,
        prevState: SequenceControllerState,
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

        val commands = props.sequenceCommander.createCommands(
            containingObjectLocation,
            SequenceConventions.stepsAttributePath,
            index,
            archetypeObjectLocation,
            clientState.graphDefinitionAttempt.graphStructure
        )

        async {
            for (command in commands) {
                ClientContext.mirroredGraphStore.apply(command)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val clientState = state.clientState
            ?: return

        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (! SequenceState.isSequence(documentNotation)) {
            return
        }

        val sequenceState = state.sequenceState
            ?: return

        if (sequenceState.globalError != null) {
            div {
                +"Error: ${sequenceState.globalError}"
            }
        }

        div {
            css {
                marginLeft = 2.em
            }

            renderMain(documentPath)
        }

        runController(clientState, sequenceState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderMain(
        documentPath: DocumentPath
    ) {
//        +"[Main]"
        MultiStepDisplay::class.react {
            common = SequenceStepDisplayPropsCommon(
                documentPath.toMainObjectLocation(),
                attributeNesting = AttributeNesting.empty,
                managed = false,
                first = true,
                last = true,
                sequenceStore = store
            )
            stepDisplayManager =
                StepDisplayManager.Handle().also {
                    it.wrapper = props.stepController
                }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.runController(
        clientState: SessionState,
        sequenceState: SequenceState
    ) {
        div {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            SequenceProgressController::class.react {
                active = clientState.clientLogicState.isActive()
                hasProgress = sequenceState.progress.hasProgress()
                sequenceProgressStore = store.progressStore
            }
        }
    }
}