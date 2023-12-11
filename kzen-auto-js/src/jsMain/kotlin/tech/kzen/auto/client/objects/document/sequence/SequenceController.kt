package tech.kzen.auto.client.objects.document.sequence

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.signature.LogicSignatureEditor
import tech.kzen.auto.client.objects.document.sequence.command.SequenceCommander
import tech.kzen.auto.client.objects.document.sequence.display.SequenceStepDisplayPropsCommon
import tech.kzen.auto.client.objects.document.sequence.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.sequence.model.SequenceGlobal
import tech.kzen.auto.client.objects.document.sequence.model.SequenceState
import tech.kzen.auto.client.objects.document.sequence.model.SequenceStore
import tech.kzen.auto.client.objects.document.sequence.progress.SequenceProgressController
import tech.kzen.auto.client.objects.document.sequence.step.control.MultiStepDisplay
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.Position
import web.cssom.em
import web.cssom.px


//-----------------------------------------------------------------------------------------------------------------
external interface SequenceControllerProps: Props {
    var stepDisplayManager: StepDisplayManager.Wrapper
    var sequenceCommander: SequenceCommander
}


external interface SequenceControllerState: State {
    var clientState: ClientState?
    var sequenceState: SequenceState?
    var creating: Boolean
}


//-----------------------------------------------------------------------------------------------------------------
class SequenceController:
    RPureComponent<SequenceControllerProps, SequenceControllerState>(),
    SequenceStore.Observer,
    InsertionGlobal.Subscriber,
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val stepWidth = 26.em

        fun stepLocations(
            graphStructure: GraphStructure,
            attributeLocation: AttributeLocation
        ): List<ObjectLocation>? {
            val stepsNotation = graphStructure
                .graphNotation
                .firstAttribute(attributeLocation)
                as? ListAttributeNotation
                ?: return null

            val objectReferenceHost = ObjectReferenceHost.ofLocation(attributeLocation.objectLocation)

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
        private val stepDisplayManager: StepDisplayManager.Wrapper,
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
                        this.stepDisplayManager = this@Wrapper.stepDisplayManager
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
        ClientContext.clientStateGlobal.observe(this)
        ClientContext.insertionGlobal.subscribe(this)
        SequenceGlobal.upsertWeak(store)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionGlobal.unsubscribe(this)
        ClientContext.clientStateGlobal.unobserve(this)
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
    override fun onSequenceState(sequenceState: SequenceState, changes: Set<SequenceStore.ChangeType>) {
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
    override fun onClientState(clientState: ClientState) {
        setState {
            this.clientState = clientState
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

        if (! SequenceConventions.isSequence(documentNotation)) {
            return
        }

        val sequenceState = state.sequenceState
            ?: return

        val mainObjectLocation = documentPath.toMainObjectLocation()
        div {
            css {
                paddingTop = 1.em
            }
            renderSignature(mainObjectLocation)
        }

        if (sequenceState.globalError != null) {
            div {
                +"Error: ${sequenceState.globalError}"
            }
        }

        div {
            css {
                marginLeft = 2.em
            }

            renderMain(mainObjectLocation)
        }

        renderRunController(clientState, sequenceState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderSignature(mainObjectLocation: ObjectLocation) {
        LogicSignatureEditor::class.react {
            objectLocation = mainObjectLocation
        }
    }


    private fun ChildrenBuilder.renderMain(
        mainObjectLocation: ObjectLocation
    ) {
        MultiStepDisplay::class.react {
            common = SequenceStepDisplayPropsCommon(
                mainObjectLocation,
                0,
                first = true,
                last = true
            )

            stepDisplayManager =
                StepDisplayManager.Handle().also {
                    it.wrapper = props.stepDisplayManager
                }

            sequenceCommander = props.sequenceCommander
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRunController(
        clientState: ClientState,
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