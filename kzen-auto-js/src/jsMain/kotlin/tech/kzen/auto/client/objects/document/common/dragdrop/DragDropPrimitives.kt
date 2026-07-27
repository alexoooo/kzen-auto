package tech.kzen.auto.client.objects.document.common.dragdrop

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.wrap.iconify.icon
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
enum class DropMarker {
    Above,
    Below
}


//---------------------------------------------------------------------------------------------------------------------
fun dropMarkerFor(
    dragSourceIndex: Int?,
    dragOverIndex: Int?,
    dropAfter: Boolean,
    index: Int
): DropMarker? {
    val source = dragSourceIndex ?: return null
    if (dragOverIndex != index) {
        return null
    }
    val rawTarget = if (dropAfter) index + 1 else index
    val newIndex = if (rawTarget > source) rawTarget - 1 else rawTarget
    if (newIndex == source) {
        return null
    }
    return if (dropAfter) DropMarker.Below else DropMarker.Above
}


fun computeDropIndex(source: Int, target: Int, dropAfter: Boolean): Int {
    val rawTarget = if (dropAfter) target + 1 else target
    return if (rawTarget > source) rawTarget - 1 else rawTarget
}


//---------------------------------------------------------------------------------------------------------------------
fun ChildrenBuilder.dragHandle(
    isVisible: Boolean,
    handleColor: Color,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    frosted: Boolean = false
) {
    div {
        // NB: selector hook so a caller can reveal the handle on hover via CSS (see ScriptStepSlot) without a
        //     hover state field. isVisible remains the "force visible" channel (e.g. while this is the drag source).
        asDynamic()["data-drag-handle"] = ""

        css {
            position = Position.absolute
            top = 0.px
            bottom = 0.px
            left = (-1.25).em
            width = 1.25.em
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            cursor = Cursor.grab
            color = handleColor
            opacity = if (isVisible) number(1.0) else number(0.0)
            if (frosted) {
                backgroundColor = Color("rgba(255, 255, 255, 0.30)")
                backdropFilter = blur(2.px)
                borderRadius = 4.px
            }
        }

        draggable = true
        onDragStart = { event ->
            event.dataTransfer.setData("text/plain", "")
            onStart()
        }
        onDragEnd = {
            onEnd()
        }

        icon("material-symbols:drag-indicator") {}
    }
}


// A filled "drop here" REGION (not a hairline): a rounded translucent-blue fill with a solid blue
// border. Absolutely positioned to fill its parent exactly (inset 0), so the caller must give the
// immediate parent position:relative (an inter-step gap strip, or a whole empty-branch region) and
// size/align it to the step-card column. pointer-events none so it never interferes with the branch's
// own drag-over hit-testing.
fun ChildrenBuilder.dropZoneRegion() {
    div {
        css {
            position = Position.absolute
            top = 0.px
            bottom = 0.px
            left = 0.px
            right = 0.px
            pointerEvents = None.none
            boxSizing = BoxSizing.borderBox
            borderRadius = 4.px
            backgroundColor = Color("rgba(41, 121, 255, 0.14)")
            border = Border(2.px, LineStyle.solid, Color("#2979ff"))
        }
    }
}


// [offset]: how far the line sits OUTSIDE the edge it marks. The default floats it in the gap between two
// step cards, where the parent is the gap strip itself. Zero for edges that butt against each other with no
// gap to float in — an If chain's section boundaries, where the line lands on the seam it will become.
fun ChildrenBuilder.dropIndicator(
    marker: DropMarker?,
    offset: Length = (-0.4).em
) {
    if (marker == null) {
        return
    }
    div {
        css {
            position = Position.absolute
            left = 0.px
            right = 0.px
            // Thicker, rounded, and glowing so the drop position reads clearly while dragging (a 2px hairline
            // was hard to see and pick precisely).
            height = 4.px
            borderRadius = 2.px
            backgroundColor = Color("#2979ff")
            boxShadow = BoxShadow(0.px, 0.px, 6.px, 1.px, Color("rgba(41, 121, 255, 0.55)"))
            pointerEvents = None.none
            when (marker) {
                DropMarker.Above -> top = offset
                DropMarker.Below -> bottom = offset
            }
        }
    }
}
