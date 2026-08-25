package tech.kzen.auto.client.objects.document.common.file

import csstype.PropertiesBuilder
import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.th
import web.cssom.*


/**
 * The one look shared by every file table: the browser's listing and the selection it feeds.
 *
 * Report established it ([tech.kzen.auto.client.objects.document.report.input.select.InputSelectedTableController]) —
 * a bordered scroller, a sticky header that survives scrolling, and rows that highlight on hover and while checked.
 * Both tables are read as one surface stacked on the same card, so a second hand-tuned palette would read as a bug.
 */
internal object FileTableColors {
    val hoverRow = Color("rgb(220, 220, 220)")
    val checkedRow = Color("rgb(220, 220, 255)")
    val checkedHoverRow = Color("rgb(190, 190, 240)")
}


internal fun PropertiesBuilder.fileTableScrollFrame(maxHeight: Length) {
    this.maxHeight = maxHeight
    overflowY = Auto.auto
    border = Border(2.px, LineStyle.solid, NamedColor.lightgray)
}


internal fun PropertiesBuilder.fileTableGrid() {
    borderCollapse = BorderCollapse.collapse
    width = 100.pct
}


/** A header cell that stays put while the body scrolls under it, underlined by an inset shadow. */
internal fun ChildrenBuilder.fileTableHeaderCell(block: ChildrenBuilder.() -> Unit) {
    th {
        css {
            position = Position.sticky
            top = 0.px
            backgroundColor = NamedColor.white
            zIndex = integer(999)
            textAlign = TextAlign.left
            paddingLeft = 0.5.em
            paddingRight = 0.5.em
            whiteSpace = WhiteSpace.nowrap
            boxShadow = BoxShadow(BoxShadowInset.inset, 0.px, (-1).px, 0.px, 0.px, NamedColor.lightgray)
        }
        block()
    }
}
