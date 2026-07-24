package tech.kzen.auto.client.objects.document.script.display.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.base.AutocompleteCloseReason
import mui.material.ClickAwayListenerMouseEvent
import mui.material.IconButton
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.material.ClickAwayListener
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.Display
import web.cssom.Position
import web.cssom.em
import web.cssom.integer
import web.cssom.pct
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface StepReferenceControllerProps: Props {
    var stepReferences: List<ObjectLocation>
    var editDisabled: Boolean

    // Parent-controlled open state: kept in sync with the shared pick session (ScriptStepReferenceStore) so
    // opening the popover also highlights the in-scope step cards in the canvas, and a canvas click / Escape
    // closes it.
    var adding: Boolean
    var onAdd: () -> Unit
    var onCancel: () -> Unit
    var onAdded: (ObjectLocation) -> Unit

    var addLabel: String
    var addIcon: String
}


//---------------------------------------------------------------------------------------------------------------------
// Presentational popover for inserting a prior in-scope Step's Kotlin variable name into an expression.
// Mirrors FormulaReferenceController (button -> filterable react-select), but the open state is owned by the
// parent (KotlinExpressionEditor) so the popover, the shared pick session, and the canvas highlight stay in
// lock-step; both a popover selection and a canvas card click feed the same insert path.
//
// The insert button stays fixed inline (it's small, so it never reflows the code field); the filterable select
// floats as an absolutely-positioned dropdown OVERTOP of the card rather than occupying an inline column —
// opening it must not disrupt the expression editor's layout. The select itself uses react-select's default
// styling (white bordered control + attached menu) so it reads as one cohesive dropdown, like the page's other
// selects — NOT the transparent-on-card transform used by inline editors, which would show the canvas through.
@Suppress("unused")
class StepReferenceController(
    props: StepReferenceControllerProps
):
    RPureComponent<StepReferenceControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                // Anchor for the absolutely-positioned dropdown, and keep the button itself inline.
                position = Position.relative
                display = Display.inlineBlock
            }

            renderToggleButton()

            if (props.adding) {
                renderPopover()
            }
        }
    }


    private fun ChildrenBuilder.renderToggleButton() {
        div {
            // While the popover is open the same button cancels it (Escape / a canvas card click also close it).
            title = if (props.adding) "Cancel" else props.addLabel

            css {
                display = Display.inlineBlock
            }

            IconButton {
                onClick = {
                    if (props.adding) {
                        props.onCancel()
                    }
                    else {
                        props.onAdd()
                    }
                }

                disabled = props.editDisabled

                icon(if (props.adding) "material-symbols:cancel" else props.addIcon) {}
            }
        }
    }


    private fun ChildrenBuilder.renderPopover() {
        // A click outside the floating dropdown ends the shared pick session (the popover then unmounts).
        // We listen on `mousedown`, NOT the default `click`: clicking the focused filter input and then the
        // stage blurs the input, and the blur-induced re-render shifts layout between mousedown and mouseup
        // so the browser never fires a `click` — which would otherwise leave the popover open until a second
        // click. mousedown fires immediately, before that re-render. The canvas step-card pick stays correct
        // because its overlay stops mousedown propagation (ScriptStepSlot.renderPickOverlay), so a card press
        // never reaches this document-level listener; the overlay's own onClick then inserts via
        // ScriptStepReferenceStore.selectStep. The Autocomplete keeps its listbox inside this subtree
        // (disablePortal) so option / scrollbar presses count as inside.
        ClickAwayListener {
            onClickAway = { _ -> props.onCancel() }
            mouseEvent = ClickAwayListenerMouseEvent.onMouseDown

            // Floating dropdown overtop of the card: it doesn't participate in the editor's flex row, so
            // opening it leaves the code field's width and position untouched. The field paints its own
            // opaque white fill (muiAutocompleteField opaqueBackground), so the canvas can't show through.
            div {
                css {
                    position = Position.absolute
                    top = 100.pct
                    right = 0.px
                    zIndex = integer(100)
                    width = 18.em
                }

                renderSelect()
            }
        }
    }


    private fun ChildrenBuilder.renderSelect() {
        val selectOptions = props
            .stepReferences
            .map {
                val option: SelectOption = unsafeJso {
                    value = it.asString()
                    label = it.objectPath.name.value
                }
                option
            }
            .toTypedArray()

        // Labelled MUI Autocomplete: mounts focused with the list pinned open (`open = true`) and inserts on
        // pick. Pinning makes the popover behave as one persistent dropdown — clicking the input can't
        // collapse the list, and a single click-away closes it. Click-away teardown is handled by the
        // surrounding ClickAwayListener (see renderPopover); disablePortal keeps the listbox inside that
        // boundary so option / scrollbar clicks count as inside. A selection unmounts the whole popover, so
        // the only close worth honouring here is Escape — which MUI reports ONLY through this reason, having
        // swallowed the keydown before any window listener could see it.
        muiAutocompleteField(
            label = "Step",
            options = selectOptions,
            // Fresh each open — the popover inserts on pick rather than holding a selection.
            selectedOption = null,
            onSelect = { props.onAdded(ObjectLocation.parse(it.value)) },
            autoFocus = true,
            disabled = props.editDisabled,
            disablePortal = true,
            open = true,
            opaqueBackground = true,
            onClose = { reason ->
                if (reason == AutocompleteCloseReason.escape) {
                    props.onCancel()
                }
            })
    }
}
