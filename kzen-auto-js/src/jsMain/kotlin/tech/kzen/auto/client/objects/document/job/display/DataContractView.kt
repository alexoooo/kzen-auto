package tech.kzen.auto.client.objects.document.job.display

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.details
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.summary
import tech.kzen.auto.client.wrap.RPureComponent
import web.cssom.Cursor
import web.cssom.Display
import web.cssom.FontFamily
import web.cssom.Margin
import web.cssom.OverflowWrap
import web.cssom.WhiteSpace
import web.cssom.em


/** Shared expandable contract presentation used by Worker cards and the channels between them. */
class DataContractView(
    props: DataContractViewProps
): RPureComponent<DataContractViewProps, State>(props) {
    override fun ChildrenBuilder.render() {
        val presentation = DataContractPresentation.of(props.display)
        if (presentation.details.isEmpty()) {
            span {
                css {
                    fontSize = 0.75.em
                    color = presentation.color
                    whiteSpace = WhiteSpace.nowrap
                }
                title = presentation.title
                +presentation.summary
            }
            return
        }

        details {
            css {
                margin = Margin(0.em, 0.em, 0.em, 0.5.em)
                color = presentation.color
                fontSize = 0.75.em
            }
            summary {
                css {
                    cursor = Cursor.pointer
                    whiteSpace = WhiteSpace.nowrap
                }
                title = presentation.title
                +presentation.summary
            }
            div {
                css {
                    display = Display.flex
                    flexDirection = web.cssom.FlexDirection.column
                    marginTop = 0.25.em
                    fontFamily = FontFamily.monospace
                    overflowWrap = OverflowWrap.anywhere
                    whiteSpace = WhiteSpace.preWrap
                }
                presentation.details.forEach { line ->
                    div { +line }
                }
            }
        }
    }
}
