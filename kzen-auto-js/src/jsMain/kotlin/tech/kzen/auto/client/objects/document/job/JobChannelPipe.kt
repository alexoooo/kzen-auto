package tech.kzen.auto.client.objects.document.job

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import web.cssom.AlignItems
import web.cssom.Border
import web.cssom.Color
import web.cssom.Display
import web.cssom.LineStyle
import web.cssom.Padding
import web.cssom.em
import web.cssom.number
import web.cssom.pct
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface JobChannelPipeProps: Props {
    var upstreamName: String
    var downstreamName: String
}


//---------------------------------------------------------------------------------------------------------------------
// The gold pipe drawn in the gap between two adjacent Worker cards the order-driven rule connects, echoing
// Flow's Pipe so a connector reads distinctly from a node. The channel is synthesized + order-managed, so the
// pipe is read-only in this iteration (materialize-on-edit config — buffer, element type — is a later phase).
// A memoized RPureComponent (props are ===-stable strings) so JobController's frequent drag-hover re-renders,
// which don't change any pipe's props, bail out here.
class JobChannelPipe(
    props: JobChannelPipeProps
):
    RPureComponent<JobChannelPipeProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val pipeFill = Color("#fff7d6")
        private val pipeBorder = Color("#e8c200")
        private val pipeAccent = Color("#9a7b00")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                width = 100.pct
            }

            // A gold connector line on each side of a central pill, so the channel reads as plumbing joining
            // the Worker cards above and below it.
            connectorLine()

            div {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    padding = Padding(0.1.em, 0.6.em)
                    borderRadius = 1.em
                    backgroundColor = pipeFill
                    border = Border(1.px, LineStyle.solid, pipeBorder)
                    color = pipeAccent
                    fontSize = 1.1.em
                }
                title = "${props.upstreamName} → ${props.downstreamName}"

                icon("material-symbols:arrow-downward") {}
            }

            connectorLine()
        }
    }


    private fun ChildrenBuilder.connectorLine() {
        div {
            css {
                flexGrow = number(1.0)
                height = 2.px
                backgroundColor = pipeBorder
            }
        }
    }
}
