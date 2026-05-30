package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.dragdrop.DropMarker
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.objects.document.common.dragdrop.dropIndicator
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptStepSlotProps: Props {
    var objectLocation: ObjectLocation
    var indexInParent: Int
    var first: Boolean
    var last: Boolean

    var dropMarker: DropMarker?
    var isDragSource: Boolean

    var stepDisplayManager: StepDisplayManager.Wrapper
    var handleColor: Color

    // NB: the two index-dependent handlers take the slot's index so the parent can hold a single stable
    //     reference for all slots (the slot threads its own indexInParent back in) — see ScriptBranchDisplay.
    var onDragStart: (Int) -> Unit
    var onDragOver: (Int, DragEvent<HTMLDivElement>) -> Unit
    var onDrop: (DragEvent<HTMLDivElement>) -> Unit
    var onDragEnd: () -> Unit
}


//---------------------------------------------------------------------------------------------------------------------
class ScriptStepSlot(
    props: ScriptStepSlotProps
):
    RPureComponent<ScriptStepSlotProps, State>(props)
{
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

                "&:hover:not(:has([data-step-slot]:hover)):not(:has([data-step-branch]:hover)) > [data-drag-handle]" {
                    opacity = number(1.0)
                }
            }

            onDragOver = { event -> props.onDragOver(props.indexInParent, event) }
            onDrop = props.onDrop

            dragHandle(
                isVisible = props.isDragSource,
                handleColor = props.handleColor,
                onStart = { props.onDragStart(props.indexInParent) },
                onEnd = props.onDragEnd,
                frosted = true)
            dropIndicator(props.dropMarker)

            props.stepDisplayManager.child(this) {
                common = commonForProps()
            }
        }
    }
}
