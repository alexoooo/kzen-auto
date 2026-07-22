package tech.kzen.auto.client.objects.document.script

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.bridge.ViewModeKey
import tech.kzen.auto.client.objects.document.common.raw.DocumentRaw
import tech.kzen.auto.client.objects.document.common.raw.DocumentRawState
import tech.kzen.auto.client.objects.document.common.raw.DocumentViewMode
import tech.kzen.auto.client.objects.document.common.signature.LogicSignatureEditor
import tech.kzen.auto.client.objects.document.common.signature.ResultSignatureEditor
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayPropsCommon
import tech.kzen.auto.client.objects.document.script.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptDependencyOverlay
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptStepDragStore
import tech.kzen.auto.client.objects.document.script.display.dependency.StepRowRefRegistry
import tech.kzen.auto.client.objects.document.script.display.edit.ScriptStepReferenceStore
import tech.kzen.auto.client.objects.document.script.model.ScriptDragStoreKey
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStepReferenceStoreKey
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.objects.document.script.model.StepRowRefRegistryKey
import tech.kzen.auto.client.objects.document.script.step.control.MultiStepDisplay
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.ViewModeGlobal
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.objects.document.script.display.ScriptMoveToArrow
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.Position
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptControllerProps: Props {
    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander
    var clientStateGlobal: ClientStateGlobal
    var clientLogicGlobal: ClientLogicGlobal
    var mirroredGraphStore: MirroredGraphStore
    var notationParser: NotationParser
    var restClient: ClientRestApi
    var objectStableMapper: ObjectStableMapper
}


external interface ScriptControllerState: State {
    var clientState: ClientState?

    // NB: only the subset of ScriptState that render consumes is stored (not the whole object), so the
    //     RPureComponent shallow-equal bails on publishes that change only per-step UI state (e.g.
    //     expand/collapse) — otherwise storing the whole ScriptState re-renders the entire Script subtree.
    var scriptLoaded: Boolean
    var globalError: String?

    // The latest run's trace snapshot, threaded to the signature editor (which reads each parameter's
    // emitted run value off it by stable id). Kept reference-stable across publishes so the RPureComponent
    // shallow-equal bails when only unrelated progress state changed.
    var logicTraceSnapshot: LogicTraceSnapshot?

    // Raw-view state. In View mode raw/editorModified are static (no typing), so they don't trigger
    // extra re-renders; in Raw mode they drive the editor and so are correctly render-consumed.
    var viewMode: DocumentViewMode
    var raw: DocumentRawState?
    var editorModified: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class ScriptController:
    RPureComponent<ScriptControllerProps, ScriptControllerState>(),
    ScriptStore.Observer,
    ClientStateGlobal.Observer,
    ViewModeGlobal.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val stepWidth = 39.em

        fun stepLocations(
            graphStructure: GraphStructure,
            attributeLocation: AttributeLocation
        ): List<ObjectLocation>? {
            if (attributeLocation.objectLocation !in graphStructure.graphNotation.coalesce) {
                // NB: deleted or renamed (this is a stale objectLocation)
                return null
            }

            // Steps in document order — the step objects nested under this branch attribute.
            return ScriptConventions.orderedDirectChildLocations(
                graphStructure.graphNotation, attributeLocation)
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
        @Service private val clientLogicGlobal: ClientLogicGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val notationParser: NotationParser,
        @Service private val restClient: ClientRestApi,
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
                        this.clientLogicGlobal = this@Wrapper.clientLogicGlobal
                        this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        this.notationParser = this@Wrapper.notationParser
                        this.restClient = this@Wrapper.restClient
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
    private val store by lazy {
        ScriptStore(props.clientStateGlobal, props.restClient, props.notationParser, props.mirroredGraphStore, props.objectStableMapper)
    }

    // NB: stable references for MultiStepDisplay's props so it (RPureComponent) bails out whenever
    //     ScriptController re-renders without the step list changing. A fresh Handle/common per render
    //     would fail the shallow === check and cascade a re-render down to every ScriptStepSlot.
    private val stepDisplayHandle = StepDisplayManager.Handle()
    private var cachedMainCommon: ScriptStepDisplayPropsCommon? = null

    // Shared drag source for cross-branch step drag/drop; provided into the per-document bridge so every
    // ScriptBranchDisplay in this script's subtree reads the same instance. One per mounted controller.
    private val dragStore = ScriptStepDragStore()

    // Shared "insert a prior Step as a value" pick session; provided into the per-document bridge so the
    // active KotlinExpressionEditor and every ScriptBranchDisplay coordinate the popover + canvas highlight.
    private val stepReferenceStore = ScriptStepReferenceStore()

    // Shared step-row rect registry for the dependency overlay, the move-to arrow and drag insertion;
    // provided into the per-document bridge so step rows and parameter rows register into one map.
    private val stepRowRefRegistry = StepRowRefRegistry()


    //-----------------------------------------------------------------------------------------------------------------
    init {
        // Single per-document context; ScriptController reads it to provide its stores (see render).
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptControllerState.init(props: ScriptControllerProps) {
        clientState = null
        scriptLoaded = false
        globalError = null
        viewMode = DocumentViewMode.View
        raw = null
        editorModified = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        store.didMount()
        store.observe(this)
        props.clientStateGlobal.observe(this)
        contextValue<DocumentBridge?>()?.channel(ViewModeKey)?.subscribe(this)
    }


    override fun componentWillUnmount() {
        contextValue<DocumentBridge?>()?.channel(ViewModeKey)?.unsubscribe(this)
        props.clientStateGlobal.unobserve(this)
        store.unobserve(this)
        store.willUnmount()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Ribbon-driven: the "Raw" tab publishes "Raw" here, any other tab publishes "" (default view).
    // The store stays the single source of truth — the body switches on store.viewMode via onScriptState.
    override fun onViewModeChanged(viewMode: String) {
        store.setViewMode(
            if (viewMode == DocumentViewMode.Raw.name) DocumentViewMode.Raw else DocumentViewMode.View)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState) {
        // NB: extract only what render reads. On expand/collapse these are unchanged, so the
        //     RPureComponent shallow-equal bails (no re-render, no fresh child props, no cascade).
        val newSnapshot = scriptState.progress.logicTraceSnapshot
        // Reuse the existing reference when value-equal (each progress refresh publishes a fresh snapshot
        // instance), so the shallow-equal still bails when the trace content didn't change.
        val stableSnapshot =
            if (newSnapshot == state.logicTraceSnapshot) state.logicTraceSnapshot else newSnapshot

        setState {
            this.scriptLoaded = true
            this.globalError = scriptState.globalError
            this.viewMode = scriptState.viewMode
            this.raw = scriptState.raw
            this.editorModified = scriptState.editorModified
            this.logicTraceSnapshot = stableSnapshot
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

        if (state.viewMode == DocumentViewMode.Raw) {
            renderRaw()
            return
        }

        val mainObjectLocation = documentPath.toMainObjectLocation()

        // Provide this script's stores into the per-document bridge so the step-display subtree reaches them
        // by key (each display installs only DocumentBridgeContext). Idempotent map writes — no re-render —
        // that also re-provide into a fresh bridge after a same-archetype document switch (ScriptController
        // isn't remounted then), and run before any child's componentDidMount.
        val bridge = contextValue<DocumentBridge?>()
        bridge?.provide(ScriptStoreKey, store)
        bridge?.provide(ScriptDragStoreKey, dragStore)
        bridge?.provide(ScriptStepReferenceStoreKey, stepReferenceStore)
        bridge?.provide(StepRowRefRegistryKey, stepRowRefRegistry)

        div {
            css {
                marginLeft = 2.em
                position = Position.relative
            }

            // NB: overlay is rendered BEFORE the signature + steps so default stacking puts it behind their
            //     cards; the cross-branch polylines (including parameter -> step) visually pass behind them.
            //     The signature shares this one relative container so a parameter row and the step that uses
            //     it align in a single dependency column and the overlay can span both.
            ScriptDependencyOverlay::class.react {
                clientStateGlobal = props.clientStateGlobal
            }

            // The signature owns its own top spacing — empty (no parameters) it adds no vertical flow, so the
            // steps start at the top with only the floating add control on the right.
            renderSignature(mainObjectLocation)

            // NB: this error slot's `div` is ALWAYS emitted (empty when there's no error) so renderMain's
            //     MultiStepDisplay below keeps a STABLE child index. As a *conditional* sibling it would
            //     index-shift the steps on every appearance/removal, and React — matching unkeyed siblings by
            //     position — would remount the whole step subtree, losing per-step expansion / scroll /
            //     in-progress editor buffers whenever a document-level (validation or progress) error toggles.
            //     Mirrors StageController.renderDefinitionErrors; empty `div` ⇒ zero footprint.
            div {
                val globalError = state.globalError
                if (globalError != null) {
                    +"Error: $globalError"
                }
            }

            renderMain(mainObjectLocation)

            // NB: mounted LAST so default stacking paints the draggable next-to-run arrow IN FRONT of the
            //     step cards (the dependency overlay, first child, stays behind). Absolute inset:0 sibling
            //     over the same relative container — it spans nested branches via the bridge-provided
            //     StepRowRefRegistry
            //     and never alters the flex-row layout the overlay's anchoring depends on.
            ScriptMoveToArrow::class.react {
                clientStateGlobal = props.clientStateGlobal
                clientLogicGlobal = props.clientLogicGlobal
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRaw() {
        val raw = state.raw
            ?: return

        div {
            css {
                paddingTop = 1.em
                marginLeft = 2.em
                marginRight = 2.em
            }

            DocumentRaw::class.react {
                rawStore = store.raw
                rawState = raw
                editorModified = state.editorModified
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderSignature(mainObjectLocation: ObjectLocation) {
        LogicSignatureEditor::class.react {
            objectLocation = mainObjectLocation
            clientStateGlobal = props.clientStateGlobal
            mirroredGraphStore = props.mirroredGraphStore
            logicTraceSnapshot = state.logicTraceSnapshot
            objectStableMapper = props.objectStableMapper
        }

        ResultSignatureEditor::class.react {
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
}