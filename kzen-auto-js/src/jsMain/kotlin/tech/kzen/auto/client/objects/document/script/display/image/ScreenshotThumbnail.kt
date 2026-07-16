package tech.kzen.auto.client.objects.document.script.display.image

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.BinaryValue
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ScreenshotThumbnailProps: Props {
    var screenshot: BinaryValue

    // This frame's global trace sequence — its identity in the page screenshot sequence, so the
    // full-screen viewer opens on this exact frame and left/right walks the page from here.
    var sequence: Long
    var objectStableMapper: ObjectStableMapper
    var clientStateGlobal: ClientStateGlobal

    // Hover delegation: reports the hovered screenshot (null on leave) so the host — the RunStep's
    // right-of-step thumbnail — shows it in its big preview. NB: plain (non-receiver) function type;
    // receiver function types are prohibited in external declarations.
    var onPreviewHover: ((BinaryValue?) -> Unit)?
}


external interface ScreenshotThumbnailState: State {
    var hovered: Boolean
    var fullscreenOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// One retained screenshot frame in a RunStep's detail film strip. Hover highlights it and drives the
// RunStep's right-of-step big preview (via onPreviewHover); click opens it full-screen. Unlike
// StepImageThumbnail it carries its screenshot directly (a specific historical frame from the trace
// timeline) and renders no floating preview of its own.
class ScreenshotThumbnail(
    props: ScreenshotThumbnailProps
):
    RPureComponent<ScreenshotThumbnailProps, ScreenshotThumbnailState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val highlightColour = Color("#1565ff")
        private val restingBorderColour = Color("rgba(0, 0, 0, 0.4)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScreenshotThumbnailState.init(props: ScreenshotThumbnailProps) {
        hovered = false
        fullscreenOpen = false
    }


    override fun componentWillUnmount() {
        // Removed mid-hover (e.g. the group collapsed) → clear the host's override so it doesn't
        // strand a stale frame in the preview.
        if (state.hovered) {
            props.onPreviewHover?.invoke(null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onEnter() {
        props.onPreviewHover?.invoke(props.screenshot)
        if (!state.hovered) {
            setState { hovered = true }
        }
    }


    private fun onLeave() {
        if (state.hovered) {
            setState { hovered = false }
            props.onPreviewHover?.invoke(null)
        }
    }


    private fun onClick() {
        setState { fullscreenOpen = true }
    }


    private fun onFullscreenClose() {
        setState { fullscreenOpen = false }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                flexShrink = number(0.0)
                padding = Padding(0.25.em, 0.25.em, 0.25.em, 0.25.em)
            }

            img {
                src = pngUrl(props.screenshot)

                css {
                    display = Display.block
                    maxWidth = 10.em
                    maxHeight = 5.em
                    // NB: 2px border at both rest and hover — only the colour changes — so the
                    //     highlight doesn't shift layout.
                    border = Border(
                        2.px,
                        LineStyle.solid,
                        if (state.hovered) highlightColour else restingBorderColour)
                    boxShadow = BoxShadow(0.px, 1.px, 3.px, Color("rgba(0, 0, 0, 0.3)"))
                    transition = "border-color 100ms ease-out".unsafeCast<Transition>()
                    cursor = Cursor.pointer
                }

                onMouseEnter = { onEnter() }
                onMouseLeave = { onLeave() }
                onClick = { onClick() }
            }
        }

        if (state.fullscreenOpen) {
            StepImageFullscreen::class.react {
                // Open on this strip frame; left/right then walks the whole page in reading order.
                initialKey = PageScreenshotEntry.frameKey(props.sequence)
                onClose = { onFullscreenClose() }
                objectStableMapper = props.objectStableMapper
                clientStateGlobal = props.clientStateGlobal
            }
        }
    }
}
