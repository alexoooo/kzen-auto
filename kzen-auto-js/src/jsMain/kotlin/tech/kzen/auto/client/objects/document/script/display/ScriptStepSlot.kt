package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.Color
import web.cssom.Cursor
import web.cssom.LineStyle
import web.cssom.Position
import web.cssom.integer
import web.cssom.number
import web.cssom.pct
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptStepSlotProps: Props {
    var objectLocation: ObjectLocation
    var indexInParent: Int
    var first: Boolean
    var last: Boolean

    var isDragSource: Boolean

    // Step-reference pick session: when true this step is a highlighted, clickable insert target. onPick
    // threads the slot's own objectLocation back so the parent can hold a single stable reference for all
    // slots (mirrors onDragStart threading indexInParent).
    var isPickTarget: Boolean
    var onPick: (ObjectLocation) -> Unit

    var stepDisplayManager: StepDisplayManager.Wrapper
    var handleColor: Color

    // NB: onDragStart takes the slot's index so the parent can hold a single stable reference for all slots
    //     (the slot threads its own indexInParent back in) — see ScriptBranchDisplay. Drag-over and drop are
    //     handled at the branch level (one drop zone), not per slot.
    var onDragStart: (Int) -> Unit
    var onDragEnd: () -> Unit
}


//---------------------------------------------------------------------------------------------------------------------
class ScriptStepSlot(
    props: ScriptStepSlotProps
):
    RPureComponent<ScriptStepSlotProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Subtle blue insert-target highlight, matching the sidebar row highlight palette.
        private val pickHighlightColor = Color("#649fff")
        private val pickHighlightTint = Color("rgba(100, 159, 255, 0.12)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: kept stable across renders so StepDisplayManager (RPureComponent) can bail out
    private var cachedCommon: ScriptStepDisplayPropsCommon? = null


    //-----------------------------------------------------------------------------------------------------------------
    private fun commonForProps(): ScriptStepDisplayPropsCommon {
        val existing = cachedCommon
        if (existing != null &&
            existing.objectLocation === props.objectLocation &&
            existing.indexInParent == props.indexInParent &&
            existing.first == props.first &&
            existing.last == props.last
        ) {
            return existing
        }
        val fresh = ScriptStepDisplayPropsCommon(
            props.objectLocation,
            props.indexInParent,
            first = props.first,
            last = props.last)
        cachedCommon = fresh
        return fresh
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            // NB: hover reveal of the drag handle is done in pure CSS (below) rather than via a hover state field.
            //     A state toggle here would re-reconcile this slot's whole sibling list on every mouse move, which
            //     re-renders nothing (RPureComponent bails out) but still flashes every sibling in React DevTools'
            //     "Highlight updates" overlay — a false positive that obscures real render work.
            //
            //     data-step-slot / data-step-branch (the latter set by ScriptBranchDisplay) mark "yield zones":
            //     an enclosing slot's handle stays hidden when the cursor is over a nested slot (e.g. a step inside
            //     an If's Then branch) or over a branch's gap/padding. :has() is descendant-only, so a slot never
            //     suppresses its own handle — only a deeper hovered slot/branch does.
            asDynamic()["data-step-slot"] = ""

            css {
                position = Position.relative
                height = 100.pct

                // Subtle outline framing this step as a click-to-insert target during a pick session. Outline
                // (not border) and an inset offset so it doesn't shift the card's layout.
                if (props.isPickTarget) {
                    borderRadius = 3.px
                    outlineWidth = 2.px
                    outlineStyle = LineStyle.solid
                    outlineColor = pickHighlightColor
                    outlineOffset = (-2).px
                }

                "&:hover:not(:has([data-step-slot]:hover)):not(:has([data-step-branch]:hover)) > [data-drag-handle]" {
                    opacity = number(1.0)
                }
            }

            dragHandle(
                isVisible = props.isDragSource,
                handleColor = props.handleColor,
                onStart = { props.onDragStart(props.indexInParent) },
                onEnd = props.onDragEnd,
                frosted = true)

            props.stepDisplayManager.child(this) {
                common = commonForProps()
            }

            // Rendered last so it overlays the card: a transparent click target that inserts this step into
            // the active expression editor (the whole card becomes clickable, taking precedence over the
            // card's own expand-on-click). Present only while this step is a pick target.
            if (props.isPickTarget) {
                renderPickOverlay()
            }
        }
    }


    private fun ChildrenBuilder.renderPickOverlay() {
        div {
            css {
                position = Position.absolute
                top = 0.px
                left = 0.px
                right = 0.px
                bottom = 0.px
                backgroundColor = pickHighlightTint
                borderRadius = 3.px
                cursor = Cursor.pointer
                zIndex = integer(1)
            }

            title = "Insert this step into the expression"

            // Stop mousedown too (not just click): the active editor's popover closes on a document-level
            // mousedown-away (StepReferenceController.renderPopover). Swallowing mousedown here keeps a card
            // press from reaching that listener, so the press inserts (onClick below) instead of cancelling.
            onMouseDown = { event ->
                event.stopPropagation()
            }

            onClick = { event ->
                event.stopPropagation()
                props.onPick(props.objectLocation)
            }
        }
    }
}
