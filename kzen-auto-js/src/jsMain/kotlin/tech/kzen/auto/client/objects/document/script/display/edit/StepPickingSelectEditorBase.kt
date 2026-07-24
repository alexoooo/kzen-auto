package tech.kzen.auto.client.objects.document.script.display.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.system.sx
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
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
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.em
import web.cssom.number
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface StepPickingSelectEditorState: SelectReferenceEditorState {
    // True while THIS editor (object + attribute) owns the shared pick session — the toggle shows cancel and
    // the in-scope step cards are highlighted.
    var picking: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// A reference-select editor whose candidates are Steps of the enclosing Script, so the value can also be chosen
// by clicking the step's card on the canvas — the gesture KotlinExpressionEditor already offers for inserting a
// reference into an expression, routed through the same shared ScriptStepReferenceStore.
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
    // Only `picking`: the base's note on its missing S.init still holds for selected/options (an unset
    // external-interface slot already reads as null), but a non-null Boolean has no such default.
    override fun StepPickingSelectEditorState.init(props: AttributeEditorProps) {
        picking = false
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
    final override fun onStepReferenceChanged() {
        val picking = referenceStore()?.session?.editorLocation == editorLocation()
        if (state.picking == picking) {
            return
        }
        setState {
            this.picking = picking
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onBeginPicking() {
        val options = state.options
            ?: return

        // The candidate set is derived from the list the dropdown is ALREADY showing rather than kept as a
        // second, drift-prone state field: option keys are full ObjectLocation strings, and parsing one back is
        // the established round trip here (see wireValue). A parallel candidate list would be freshly allocated
        // on every publish and would need its own content compare (cf. setOptions) to keep the bail-out working.
        //
        // Candidates that render no step card (Script parameters / ForEach item bindings, which are addressable
        // values but not steps) are simply never matched by ScriptBranchDisplay — they stay dropdown-only.
        val candidates = options.map { ObjectLocation.parse(it.value) }.toSet()

        referenceStore()?.begin(editorLocation(), candidates) { stepLocation ->
            // The picked location IS one of the option keys, so it feeds the base's single notation-write path
            // directly; wireValue() does the crop at commit time.
            selectAndCommit(stepLocation.asString())
        }
    }


    private fun onEndPicking() {
        referenceStore()?.end(editorLocation())
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The field plus its pick toggle, sharing a flex row that owns the sizing which keeps the button from
    // overflowing (same layout as SelectLogicEditor's launch button).
    private fun ChildrenBuilder.selectFieldWithPickToggle(options: Array<SelectOption>) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
            }

            // The select grows; minWidth 0 lets it shrink so the toggle never overflows.
            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                }

                selectField(options)
            }

            IconButton {
                sx {
                    marginLeft = 0.25.em
                }
                // While armed the same button cancels; Escape and picking a card also end the session.
                title = if (state.picking) "Cancel" else "Pick a step from the canvas"
                disabled = options.isEmpty()

                onClick = {
                    if (state.picking) {
                        onEndPicking()
                    }
                    else {
                        onBeginPicking()
                    }
                }

                icon(if (state.picking) "material-symbols:cancel" else "material-symbols:ads-click") {
                    style = unsafeJso {
                        fontSize = 1.25.em
                    }
                }
            }
        }
    }


    override fun ChildrenBuilder.render() {
        val options = state.options
            ?: return

        selectFieldWithPickToggle(options)
    }
}
