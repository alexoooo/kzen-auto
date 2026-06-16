package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.div
import tech.kzen.lib.common.model.document.DocumentPath
import web.cssom.*
import web.data.AllowedEffect
import web.data.move
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
// Shared geometry + flat layout for a sidebar row (folder header or file).
//
// Every row is width:100% of the tree's inline-block wrapper (see SidebarController), which shrink-wraps to the
// widest row's content. So all rows share one width — the scroll-content width — which is what lets each row's
// trailing SidebarItemMenu (position:sticky; right:0) stay glued to the visible right edge uniformly while the
// sidebar is scrolled horizontally (a row that's only as wide as the viewport can't keep its sticky child in view).
//
// Indentation is a per-row left pad derived from tree depth (NOT nested width-shrinking containers), so deeper
// rows don't get narrower and break the shared width.
object SidebarRow {
    val rowHeight = 2.em
    val iconWidth = 22.px

    // chevron (folders) / alignment spacer (files) at the start of a row's own content
    val leadingSlot = 1.25.em

    // left pad added per nesting level
    private const val indentStepEm = 1.0

    fun indent(depth: Int): Length =
        (depth * indentStepEm).em
}


fun ChildrenBuilder.sidebarRow(
    depth: Int,
    // highlighted = this row is the active drag-and-drop target (a folder a document/folder is hovering over)
    highlighted: Boolean = false,
    // hook to set drag-and-drop attributes (draggable / handlers) directly on the row div — see sidebarDragSource
    rowAttributes: (HTMLAttributes<HTMLDivElement>.() -> Unit)? = null,
    block: ChildrenBuilder.() -> Unit
) {
    div {
        css {
            position = Position.relative
            display = Display.flex
            alignItems = AlignItems.center
            height = SidebarRow.rowHeight
            width = 100.pct
            boxSizing = BoxSizing.borderBox
            paddingLeft = SidebarRow.indent(depth)

            if (highlighted) {
                backgroundColor = Color("rgba(100, 159, 255, 0.18)")
                // outline (not border) so the marker doesn't shift the row's content; inset so it stays
                // within the sidebar's horizontal overflow clip
                outlineWidth = 2.px
                outlineStyle = LineStyle.solid
                outlineColor = Color("#649fff")
                outlineOffset = (-2).px
            }

            SidebarItemMenu.revealOnHoverSelector {
                opacity = number(1.0)
            }
        }

        rowAttributes?.invoke(this)

        block()
    }
}


// Makes a sidebar row draggable as a move source. The dropped-on folder reads the live source from the shared
// dragSourcePath (set via onDragItemStart) rather than from the dataTransfer payload — the text/plain data is
// set only because some browsers require non-empty drag data to initiate a drag.
fun HTMLAttributes<HTMLDivElement>.sidebarDragSource(
    sourcePath: DocumentPath,
    onDragItemStart: (DocumentPath) -> Unit,
    onDragItemEnd: () -> Unit
) {
    draggable = true

    onDragStart = { event ->
        event.dataTransfer.setData("text/plain", sourcePath.asString())
        event.dataTransfer.effectAllowed = AllowedEffect.move
        onDragItemStart(sourcePath)
    }

    onDragEnd = {
        onDragItemEnd()
    }
}
