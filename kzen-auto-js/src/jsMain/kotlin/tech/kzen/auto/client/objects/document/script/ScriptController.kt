package tech.kzen.auto.client.objects.document.script

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.signature.LogicSignatureEditor
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayPropsCommon
import tech.kzen.auto.client.objects.document.script.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.script.model.ScriptGlobal
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.progress.ScriptProgressController
import tech.kzen.auto.client.objects.document.script.step.control.MultiStepDisplay
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
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
external interface ScriptControllerProps: Props {
    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander
}


external interface ScriptControllerState: State {
    var clientState: ClientState?
    var scriptState: ScriptState?
    var creating: Boolean
}


//-----------------------------------------------------------------------------------------------------------------
class ScriptController:
    RPureComponent<ScriptControllerProps, ScriptControllerState>(),
    ScriptStore.Observer,
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
        private val scriptCommander: ScriptCommander,
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
                    ScriptController::class.react {
                        this.stepDisplayManager = this@Wrapper.stepDisplayManager
                        this.scriptCommander = this@Wrapper.scriptCommander
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = ScriptStore()


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptControllerState.init(props: ScriptControllerProps) {
        clientState = null
        scriptState = null
        creating = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        store.didMount()
        store.observe(this)
        ClientContext.clientStateGlobal.observe(this)
        ClientContext.insertionGlobal.subscribe(this)
        ScriptGlobal.upsertWeak(store)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionGlobal.unsubscribe(this)
        ClientContext.clientStateGlobal.unobserve(this)
        store.unobserve(this)
        store.willUnmount()
    }


    override fun componentDidUpdate(
        prevProps: ScriptControllerProps,
        prevState: ScriptControllerState,
        snapshot: Any
    ) {
//        val clientState = state.clientState
//                ?: return
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState, changes: Set<ScriptStore.ChangeType>) {
        setState {
            this.scriptState = scriptState
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

        if (!ScriptConventions.isScript(documentNotation)) {
            return
        }

        val scriptState = state.scriptState
            ?: return

        val mainObjectLocation = documentPath.toMainObjectLocation()
        div {
            css {
                paddingTop = 1.em
            }
            renderSignature(mainObjectLocation)
        }

        if (scriptState.globalError != null) {
            div {
                +"Error: ${scriptState.globalError}"
            }
        }

        div {
            css {
                marginLeft = 2.em
            }

            renderMain(mainObjectLocation)
        }

        renderRunController(clientState, scriptState)
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
            common = ScriptStepDisplayPropsCommon(
                mainObjectLocation,
                0,
                first = true,
                last = true
            )

            stepDisplayManager =
                StepDisplayManager.Handle().also {
                    it.wrapper = props.stepDisplayManager
                }

            scriptCommander = props.scriptCommander
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRunController(
        clientState: ClientState,
        scriptState: ScriptState
    ) {
        div {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            ScriptProgressController::class.react {
                active = clientState.clientLogicState.isActive()
                hasProgress = scriptState.progress.hasProgress()
                scriptProgressStore = store.progressStore
            }
        }
    }
}