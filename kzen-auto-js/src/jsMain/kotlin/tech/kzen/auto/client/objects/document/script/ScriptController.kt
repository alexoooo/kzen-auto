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
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptDependencyOverlay
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayPropsCommon
import tech.kzen.auto.client.objects.document.script.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreContext
import tech.kzen.auto.client.objects.document.script.progress.ScriptProgressController
import tech.kzen.auto.client.objects.document.script.step.control.MultiStepDisplay
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
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
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.Position
import web.cssom.em
import web.cssom.px


//-----------------------------------------------------------------------------------------------------------------
external interface ScriptControllerProps: Props {
    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
    var restClient: ClientRestApi
    var insertionGlobal: InsertionGlobal
    var objectStableMapper: ObjectStableMapper
}


external interface ScriptControllerState: State {
    var clientState: ClientState?

    // NB: only the subset of ScriptState that render consumes is stored (not the whole object), so the
    //     RPureComponent shallow-equal bails on publishes that change only per-step UI state (e.g.
    //     expand/collapse) — otherwise storing the whole ScriptState re-renders the entire Script subtree.
    var scriptLoaded: Boolean
    var globalError: String?
    var hasProgress: Boolean
}


//-----------------------------------------------------------------------------------------------------------------
class ScriptController:
    RPureComponent<ScriptControllerProps, ScriptControllerState>(),
    ScriptStore.Observer,
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val stepWidth = 39.em

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
        private val ribbonController: RibbonController.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val restClient: ClientRestApi,
        @Service private val insertionGlobal: InsertionGlobal,
        @Service private val objectStableMapper: ObjectStableMapper
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
                        this.clientStateGlobal = this@Wrapper.clientStateGlobal
                        this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        this.restClient = this@Wrapper.restClient
                        this.insertionGlobal = this@Wrapper.insertionGlobal
                        this.objectStableMapper = this@Wrapper.objectStableMapper
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: lazy so the store is constructed from props (set by React after the no-arg ctor runs the field
    //     initializers). First access is componentDidMount, by which point props are available; the lazy
    //     value is computed once, keeping a stable reference for renders and the progress sub-store prop.
    private val store by lazy { ScriptStore(props.clientStateGlobal, props.restClient) }

    // NB: stable references for MultiStepDisplay's props so it (RPureComponent) bails out whenever
    //     ScriptController re-renders without the step list changing. A fresh Handle/common per render
    //     would fail the shallow === check and cascade a re-render down to every ScriptStepSlot.
    private val stepDisplayHandle = StepDisplayManager.Handle()
    private var cachedMainCommon: ScriptStepDisplayPropsCommon? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptControllerState.init(props: ScriptControllerProps) {
        clientState = null
        scriptLoaded = false
        globalError = null
        hasProgress = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        store.didMount()
        store.observe(this)
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
        store.unobserve(this)
        store.willUnmount()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState) {
        // NB: extract only what render reads. On expand/collapse these are unchanged, so the
        //     RPureComponent shallow-equal bails (no re-render, no fresh child props, no cascade).
        setState {
            this.scriptLoaded = true
            this.globalError = scriptState.globalError
            this.hasProgress = scriptState.progress.hasProgress()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        // NB: ClientState is a data class — structural equality. Skip setState on no-op publishes so the entire
        //     Script subtree isn't re-reconciled (which would flash every descendant in DevTools' overlay).
        if (state.clientState == clientState) {
            return
        }
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

        if (!state.scriptLoaded) {
            return
        }

        val mainObjectLocation = documentPath.toMainObjectLocation()
        ScriptStoreContext.Provider(store) {
            div {
                css {
                    paddingTop = 1.em
                }
                renderSignature(mainObjectLocation)
            }

            val globalError = state.globalError
            if (globalError != null) {
                div {
                    +"Error: $globalError"
                }
            }

            div {
                css {
                    marginLeft = 2.em
                    position = Position.relative
                }

                // NB: overlay is rendered BEFORE MultiStepDisplay so default stacking puts it behind
                //     step cards; the cross-branch polylines visually pass behind the IfStep card.
                ScriptDependencyOverlay::class.react {
                    clientStateGlobal = props.clientStateGlobal
                }

                renderMain(mainObjectLocation)
            }

            renderRunController(clientState)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderSignature(mainObjectLocation: ObjectLocation) {
        LogicSignatureEditor::class.react {
            objectLocation = mainObjectLocation
            clientStateGlobal = props.clientStateGlobal
            mirroredGraphStore = props.mirroredGraphStore
        }
    }


    private fun ChildrenBuilder.renderMain(
        mainObjectLocation: ObjectLocation
    ) {
        stepDisplayHandle.wrapper = props.stepDisplayManager

        MultiStepDisplay::class.react {
            common = mainCommonFor(mainObjectLocation)
            stepDisplayManager = stepDisplayHandle
            scriptCommander = props.scriptCommander
            clientStateGlobal = props.clientStateGlobal
            insertionGlobal = props.insertionGlobal
            mirroredGraphStore = props.mirroredGraphStore
            objectStableMapper = props.objectStableMapper
        }
    }


    // NB: value compare (not ===) — mainObjectLocation is freshly derived from documentPath each render,
    //     so rebuild common only when the document actually changes, keeping the reference stable otherwise.
    private fun mainCommonFor(mainObjectLocation: ObjectLocation): ScriptStepDisplayPropsCommon {
        val existing = cachedMainCommon
        if (existing != null && existing.objectLocation == mainObjectLocation) {
            return existing
        }
        val fresh = ScriptStepDisplayPropsCommon(
            mainObjectLocation,
            0,
            first = true,
            last = true)
        cachedMainCommon = fresh
        return fresh
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRunController(
        clientState: ClientState
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
                hasProgress = state.hasProgress
                scriptProgressStore = store.progressStore
            }
        }
    }
}