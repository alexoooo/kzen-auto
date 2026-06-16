package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import web.cssom.*


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


fun ChildrenBuilder.sidebarRow(depth: Int, block: ChildrenBuilder.() -> Unit) {
    div {
        css {
            position = Position.relative
            display = Display.flex
            alignItems = AlignItems.center
            height = SidebarRow.rowHeight
            width = 100.pct
            boxSizing = BoxSizing.borderBox
            paddingLeft = SidebarRow.indent(depth)

            SidebarItemMenu.revealOnHoverSelector {
                opacity = number(1.0)
            }
        }

        block()
    }
}
