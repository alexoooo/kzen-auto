package tech.kzen.auto.client.objects.document.script.display

import react.State
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.objects.document.script.model.StepValidation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


//---------------------------------------------------------------------------------------------------------------------
// The services every step display needs: the broadcast global it subscribes to, the stable mapper its trace
// lookup goes through, and the graph store its header edits go to.
external interface ScriptStepDisplayBaseProps: ScriptStepDisplayProps {
    var clientStateGlobal: ClientStateGlobal
    var objectStableMapper: ObjectStableMapper
    var mirroredGraphStore: MirroredGraphStore
}


// The slice every step display derives from the two stores: what its header shows, and this step's live
// trace / validation.
external interface ScriptStepDisplayBaseState: State {
    var icon: String?
    var description: String?
    var title: String?

    var stepTrace: StepTrace?
    var isNextToRun: Boolean?
    var stepValidation: StepValidation?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Shared skeleton for the step displays that subscribe to both [ClientStateGlobal] and the document's
 * [ScriptStore] (the leaf card and the branch-bearing control steps). It owns the two subscriptions, the
 * derivation of the common slice, and — the reason it exists — the value-equality skip guards.
 *
 * Both stores publish the FULL state to EVERY subscriber on ANY change, and the derived values are freshly
 * allocated each publish ([computeStepTraceInfo] rebuilds a value-equal but non-identical [StepTrace]), so an
 * unconditional `setState` defeats [RPureComponent] and re-renders every step body on every sibling's edit or
 * progress tick. Guarding that is the render-scoping discipline of `docs/js-architecture.md` §2, and holding it
 * here makes it structural: a subclass cannot skip a guard it never sees.
 *
 * A subclass with an additional slice overrides [onClientStateExtra] / [onScriptStateExtra] and guards only its
 * own fields there; React batches the two partial `setState` calls into a single render.
 */
abstract class ScriptStepDisplayBase<P: ScriptStepDisplayBaseProps, S: ScriptStepDisplayBaseState>(
    props: P
):
    RPureComponent<P, S>(props),
    ClientStateGlobal.Observer,
    ScriptStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The document's store, reached through the bridge context. Null when no Script document provided one.
    protected fun scriptStore(): ScriptStore? {
        return contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)
    }


    //-----------------------------------------------------------------------------------------------------------------
    final override fun componentDidMount() {
        // NB: ScriptStore.observe replays onScriptState synchronously with the current state.
        props.clientStateGlobal.observe(this)
        scriptStore()?.observe(this)
        onStepMount()
    }


    final override fun componentWillUnmount() {
        // Unobserve BEFORE the subclass hook: a hook that writes back into the store (see
        // ScriptStepDisplayDefault's expansion prune) must not publish onScriptState into this unmounting
        // component - the still-mounted siblings are the ones that should react.
        scriptStore()?.unobserve(this)
        props.clientStateGlobal.unobserve(this)
        onStepUnmount()
    }


    // Attach / detach anything beyond the two standard subscriptions here rather than overriding the lifecycle
    // methods, so no subclass has to remember a super call.
    protected open fun onStepMount() {}


    protected open fun onStepUnmount() {}


    //-----------------------------------------------------------------------------------------------------------------
    final override fun onClientState(clientState: ClientState) {
        val info = computeStepHeaderInfo(clientState, props.common.objectLocation)
            ?: return

        if (state.icon != info.icon ||
            state.description != info.description ||
            state.title != info.title
        ) {
            setState {
                this.icon = info.icon
                this.description = info.description
                this.title = info.title
            }
        }

        onClientStateExtra(clientState)
    }


    final override fun onScriptState(scriptState: ScriptState) {
        val info = computeStepTraceInfo(
            scriptState, props.common.objectLocation, props.objectStableMapper)

        val stepValidation = scriptState
            .validationState
            .scriptValidation
            ?.stepValidations
            ?.get(props.common.objectLocation.objectPath)

        // NB: value compare (==), not === - computeStepTraceInfo rebuilds a fresh StepTrace each call (its
        //     fields come from the stable trace map, so it is value-equal but not identical), and a reference
        //     guard would therefore never bail.
        if (state.stepTrace != info.trace ||
            state.isNextToRun != info.isNextToRun ||
            state.stepValidation != stepValidation
        ) {
            setState {
                this.stepTrace = info.trace
                this.isNextToRun = info.isNextToRun
                this.stepValidation = stepValidation
            }
        }

        onScriptStateExtra(scriptState)
    }


    // Derive any additional slice here. Each override guards and writes ONLY its own fields - the common slice
    // above is already handled.
    protected open fun onClientStateExtra(clientState: ClientState) {}


    protected open fun onScriptStateExtra(scriptState: ScriptState) {}
}
