package tech.kzen.auto.client.objects.document.script.display.image

import emotion.react.css
import mui.material.Modal
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.exec.BinaryExecutionValue
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ScreenshotFullscreenProps: Props {
    var screenshot: BinaryExecutionValue
    var label: String
    var onClose: () -> Unit
}


//---------------------------------------------------------------------------------------------------------------------
// Minimal full-screen viewer for a single retained screenshot frame (a specific historical trace
// event), distinct from StepImageFullscreen which resolves the latest-per-step frame from the live
// snapshot and navigates between steps. Click anywhere or Esc (MUI Modal) to dismiss; cross-frame
// timeline navigation is a later refinement.
class ScreenshotFullscreen(
    props: ScreenshotFullscreenProps
):
    RPureComponent<ScreenshotFullscreenProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        Modal {
            open = true
            // NB: MUI Modal's onClose fires for both backdrop click and Escape; treat both as dismiss.
            onClose = { _, _ -> props.onClose() }

            div {
                css {
                    position = Position.fixed
                    top = 0.px
                    left = 0.px
                    width = 100.vw
                    height = 100.vh
                    display = Display.flex
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center
                    backgroundColor = Color("rgba(0, 0, 0, 0.92)")
                    cursor = Cursor.pointer
                }
                onClick = { props.onClose() }

                renderHeader()

                img {
                    src = pngUrl(props.screenshot)
                    css {
                        display = Display.block
                        maxWidth = 100.vw
                        maxHeight = 100.vh
                        cursor = Cursor.pointer
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderHeader() {
        div {
            css {
                position = Position.fixed
                top = 0.px
                left = 0.px
                width = 100.vw
                boxSizing = BoxSizing.borderBox
                padding = Padding(0.75.em, 1.em, 0.75.em, 1.em)
                color = NamedColor.white
                textAlign = TextAlign.center
                backgroundImage = linearGradient(
                    stop(Color("rgba(0, 0, 0, 0.55)"), 0.px),
                    stop(Color("rgba(0, 0, 0, 0)"), 100.pct))
                pointerEvents = None.none
            }

            div {
                css {
                    fontSize = 1.1.em
                    fontWeight = FontWeight.bold
                }
                +props.label
            }

            div {
                css {
                    fontSize = 0.85.em
                    opacity = number(0.8)
                    marginTop = 0.25.em
                }
                +"Click anywhere or press Esc to close"
            }
        }
    }
}
