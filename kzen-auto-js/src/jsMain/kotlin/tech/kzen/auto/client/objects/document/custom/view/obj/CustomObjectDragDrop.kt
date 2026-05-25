package tech.kzen.auto.client.objects.document.custom.view.obj

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.custom.CustomTheme
import tech.kzen.auto.client.objects.document.custom.view.CustomViewStore
import tech.kzen.auto.client.wrap.material.iconByName
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
enum class DropMarker {
    Above,
    Below
}


//---------------------------------------------------------------------------------------------------------------------
object CustomObjectDragDrop {
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
}


//---------------------------------------------------------------------------------------------------------------------
fun ChildrenBuilder.customDragHandle(
    isVisible: Boolean,
    indexInDocument: Int,
    viewStore: CustomViewStore
) {
    div {
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
            color = CustomTheme.mutedText
            opacity = if (isVisible) number(1.0) else number(0.0)
        }

        draggable = true
        onDragStart = { event ->
            event.dataTransfer.setData("text/plain", "")
            viewStore.onDragStart(indexInDocument)
        }
        onDragEnd = {
            viewStore.onDragEnd()
        }

        iconByName("DragIndicator") {}
    }
}


fun ChildrenBuilder.customDropIndicator(marker: DropMarker?) {
    if (marker == null) {
        return
    }
    div {
        css {
            position = Position.absolute
            left = 0.px
            right = 0.px
            height = 2.px
            backgroundColor = Color("#649fff")
            pointerEvents = None.none
            when (marker) {
                DropMarker.Above -> top = (-0.5).em
                DropMarker.Below -> bottom = (-0.5).em
            }
        }
    }
}
