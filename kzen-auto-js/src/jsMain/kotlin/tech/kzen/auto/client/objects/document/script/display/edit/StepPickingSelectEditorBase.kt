package tech.kzen.auto.client.objects.document.script.display.edit

import react.ChildrenBuilder
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorBase
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.objects.document.script.model.ScriptStepReferenceStoreKey
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation


//---------------------------------------------------------------------------------------------------------------------
external interface StepPickingSelectEditorState: SelectReferenceEditorState {
    // The dropdown's open state, held here rather than left to MUI because it IS the pick session: opening
    // arms the shared session, closing ends it, and a session ended from elsewhere — a canvas card click,
    // Escape, another editor taking over — has to collapse the listbox in turn.
    var open: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// A reference-select editor whose candidates are Steps of the enclosing Script, so the value can also be chosen
// by clicking the step's card on the canvas — the gesture KotlinExpressionEditor already offers for inserting a
// reference into an expression, routed through the same shared ScriptStepReferenceStore.
//
// The gesture is folded INTO the dropdown rather than offered beside it as an arm/cancel button: while the
// listbox is open the in-scope step cards are outlined and clickable, so the list and the canvas are two views
// of one candidate set, and every way of dismissing a dropdown — click-away, Escape, clicking the field again,
// picking an option — is also the way to cancel the pick. Only one session exists at a time, so opening this
// dropdown ends whatever was picking before.
//
// Deliberately NOT folded into SelectReferenceEditorBase: that base is document-agnostic and shared with editors
// that have nothing to do with Scripts (SelectObjectEditor, SelectLogicEditor), whereas ScriptStepReferenceStore
// and ScriptStore are script-specific. This subclass is where that script knowledge is allowed to live.
abstract class StepPickingSelectEditorBase(
    props: AttributeEditorProps
):
    SelectReferenceEditorBase<AttributeEditorProps, StepPickingSelectEditorState>(props),
    ClientStateGlobal.Observer,
    ScriptStore.Observer,
    ScriptStepReferenceStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Only `open`: the base's note on its missing S.init still holds for selected/options (an unset
    // external-interface slot already reads as null), but a non-null Boolean has no such default.
    override fun StepPickingSelectEditorState.init(props: AttributeEditorProps) {
        open = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onMount() {
        props.clientStateGlobal.observe(this)
        scriptStore()?.observe(this)
        referenceStore()?.observe(this)
    }


    override fun onUnmount() {
        // Unobserve the reference store before ending the session, so the resulting clear/publish doesn't call
        // back into this unmounting component.
        val referenceStore = referenceStore()
        referenceStore?.unobserve(this)
        referenceStore?.end(editorLocation())

        scriptStore()?.unobserve(this)
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scriptStore(): ScriptStore? =
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)


    private fun referenceStore(): ScriptStepReferenceStore? =
        contextValue<DocumentBridge?>()?.lookup(ScriptStepReferenceStoreKey)


    // Attribute-scoped pick-session identity — see ScriptStepReferenceStore.Session.editorLocation for why.
    // NB: a function, not a cached val — these editors outlive a rename of their own host (the manager
    // re-renders them with a new objectLocation), and a property initializer would pin the FIRST render's
    // props, the shadowing hazard documented on SelectReferenceEditorBase's committer.
    private fun editorLocation(): AttributeLocation =
        AttributeLocation(props.objectLocation, AttributePath.ofName(props.attributeName))


    //-----------------------------------------------------------------------------------------------------------------
    // The session ending elsewhere closes this dropdown. Only ever closes, never opens: begin() publishes
    // synchronously from inside onFieldOpen below, while that gesture's setState is still pending, so an
    // ownership check alone would read as "someone else's session" and slam the listbox shut on open.
    final override fun onStepReferenceChanged() {
        if (! state.open) {
            return
        }
        if (referenceStore()?.session?.editorLocation == editorLocation()) {
            return
        }
        setState {
            open = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onFieldOpen() {
        setState {
            open = true
        }

        // The candidate set is derived from the list the dropdown is ALREADY showing rather than kept as a
        // second, drift-prone state field: option keys are full ObjectLocation strings, and parsing one back is
        // the established round trip here (see wireValue). A parallel candidate list would be freshly allocated
        // on every publish and would need its own content compare (cf. setOptions) to keep the bail-out working.
        //
        // Candidates that render no step card (Script parameters / ForEach item bindings, which are addressable
        // values but not steps) are simply never matched by ScriptBranchDisplay — they stay dropdown-only.
        val options = state.options
            ?: emptyArray<SelectOption>()
        val candidates = options.map { ObjectLocation.parse(it.value) }.toSet()

        referenceStore()?.begin(editorLocation(), candidates) { stepLocation ->
            // The picked location IS one of the option keys, so it feeds the base's single notation-write path
            // directly; wireValue() does the crop at commit time.
            selectAndCommit(stepLocation.asString())
        }
    }


    private fun onFieldClose() {
        setState {
            open = false
        }
        referenceStore()?.end(editorLocation())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val options = state.options
            ?: return

        selectField(
            options,
            open = state.open,
            onOpen = { onFieldOpen() },
            onClose = { onFieldClose() })
    }
}
