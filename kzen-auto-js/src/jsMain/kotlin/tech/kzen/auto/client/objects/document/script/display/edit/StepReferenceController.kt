package tech.kzen.auto.client.objects.document.script.display.edit

import emotion.react.css
import js.objects.unsafeJso
import kotlinx.browser.document
import mui.material.IconButton
import mui.material.InputLabel
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
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
            title = if (props.adding) "CancelB" else props.addLabel

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
        // Floating dropdown overtop of the card: it doesn't participate in the editor's flex row, so opening it
        // leaves the code field's width and position untouched. No surface of its own — the react-select control
        // is its own bordered box and the menu attaches below it, so the two read as a single dropdown.
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


    private fun ChildrenBuilder.renderSelect() {
        val selectOptions = props
            .stepReferences
            .map {
                val option: ReactSelectOption = unsafeJso {
                    value = it.asString()
                    label = it.objectPath.name.value
                }
                option
            }
            .toTypedArray()

        InputLabel {
            css {
                fontSize = 0.8.em
            }

            +"Step"

            ReactSelect::class.react {
                // Fresh each open — the popover inserts on pick rather than holding a selection.
                value = null
                options = selectOptions

                // Opens focused with the list shown, so it reads as a primitive autocomplete.
                autoFocus = true
                defaultMenuIsOpen = true

                onChange = {
                    props.onAdded(ObjectLocation.parse(it.value))
                }

                isDisabled = props.editDisabled

                // NB: prevents clipping when the dropdown overflows the card; the menu still anchors to the
                //     control, so portaling doesn't detach it visually (matches the page's other selects).
                menuPortalTarget = document.body!!
            }
        }
    }
}
