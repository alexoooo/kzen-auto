package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import js.reflect.unsafeCast
import kotlinx.browser.window
import mui.material.Modal
import react.ChildrenBuilder
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreContext
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.platform.IoUtils
import web.cssom.*
import web.html.HTMLImageElement


//---------------------------------------------------------------------------------------------------------------------
external interface StepScreenshotPreviewProps: Props {
    var objectLocation: ObjectLocation
}


external interface StepScreenshotPreviewState: State {
    var screenshot: BinaryExecutionValue?

    var hovered: Boolean
    var floatingTop: Double
    var floatingLeft: Double

    var modalOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class StepScreenshotPreview(
    props: StepScreenshotPreviewProps
):
    RComponent<StepScreenshotPreviewProps, StepScreenshotPreviewState>(props),
    ScriptStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val highlightColour = Color("#649fff")
        private val restingBorderColour = Color("rgba(0, 0, 0, 0.2)")

        // NB: matches maxWidth/maxHeight on the floating preview; used in onMouseEnter to
        //     compute viewport-aware placement. emToPx assumes the default 16px root font.
        private const val EM_TO_PX = 16.0
        private const val PREVIEW_MAX_W_PX = 50 * EM_TO_PX  // 800
        private const val PREVIEW_MAX_H_PX = 25 * EM_TO_PX  // 400
        private const val GAP_PX = 8.0
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val imgRef: RefObject<HTMLImageElement> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(ScriptStoreContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        contextValue<ScriptStore?>()?.observe(this)
    }


    override fun componentWillUnmount() {
        contextValue<ScriptStore?>()?.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepScreenshotPreviewState.init(props: StepScreenshotPreviewProps) {
        screenshot = null
        hovered = false
        floatingTop = 0.0
        floatingLeft = 0.0
        modalOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState) {
        val traceInfo = computeStepTraceInfo(
            scriptState, props.objectLocation, ClientContext.objectStableMapper)
        val next = traceInfo.trace?.detail as? BinaryExecutionValue

        if (state.screenshot === next) {
            return
        }

        setState {
            this.screenshot = next
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onThumbnailEnter() {
        val rect = imgRef.current?.getBoundingClientRect()
            ?: return

        val vh = window.innerHeight.toDouble()

        // NB: always place to the right of the thumbnail. No left-side fallback — flipping
        //     to the left would put the preview under the sidebar on narrow windows. If
        //     the viewport is too narrow to fit the full preview on the right, accept the
        //     right-edge clipping (matches the prior CSS scale behaviour).
        val left = rect.right + GAP_PX

        // Vertical: align with thumbnail's top, then clamp so the preview stays within
        // the viewport — addresses the original bottom-of-page issue.
        val top = rect.top
            .coerceAtMost(vh - PREVIEW_MAX_H_PX - GAP_PX)
            .coerceAtLeast(GAP_PX)

        setState {
            hovered = true
            floatingLeft = left
            floatingTop = top
        }
    }


    private fun onThumbnailLeave() {
        if (state.hovered) {
            setState {
                hovered = false
            }
        }
    }


    private fun onThumbnailClick() {
        setState {
            modalOpen = true
        }
    }


    private fun onModalClose() {
        setState {
            modalOpen = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val screenshot = state.screenshot
            ?: return

        val screenshotPngUrl = screenshot.cache("img") {
            val base64 = IoUtils.base64Encode(screenshot.value)
            "data:png/png;base64,$base64"
        }

        renderThumbnail(screenshotPngUrl)

        if (state.hovered) {
            renderFloatingPreview(screenshotPngUrl)
        }

        if (state.modalOpen) {
            renderModalViewer(screenshotPngUrl)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderThumbnail(pngUrl: String) {
        div {
            css {
                marginLeft = 1.em
                flexShrink = number(0.0)
                padding = Padding(0.25.em, 0.25.em, 0.25.em, 0.25.em)
            }

            img {
                ref = imgRef
                src = pngUrl

                css {
                    display = Display.block
                    // NB: resting size sized to a Step row with trace (~5em tall); 10em wide
                    //     cap accommodates typical 16:9 browser screenshots plus headroom.
                    maxWidth = 10.em
                    maxHeight = 5.em

                    // NB: 2px border at both rest and hover — only the colour changes —
                    //     so toggling the highlight doesn't shift layout by 1px on/off.
                    border = Border(
                        2.px,
                        LineStyle.solid,
                        if (state.hovered) highlightColour else restingBorderColour)
                    transition = "border-color 100ms ease-out".unsafeCast<Transition>()

                    cursor = Cursor.pointer
                }

                onMouseEnter = { onThumbnailEnter() }
                onMouseLeave = { onThumbnailLeave() }
                onClick = { onThumbnailClick() }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderFloatingPreview(pngUrl: String) {
        img {
            src = pngUrl

            css {
                position = Position.fixed
                top = state.floatingTop.px
                left = state.floatingLeft.px
                maxWidth = 50.em
                maxHeight = 25.em
                display = Display.block

                border = Border(2.px, LineStyle.solid, highlightColour)
                // NB: opaque background so partially-transparent PNGs don't bleed through.
                backgroundColor = NamedColor.white
                boxShadow = BoxShadow(0.px, 4.px, 16.px, Color("rgba(0, 0, 0, 0.25)"))

                zIndex = integer(100)

                // NB: cursor passes through the preview, so onMouseLeave on the thumbnail
                //     fires the moment the cursor leaves it — clean tooltip dismissal.
                pointerEvents = None.none
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderModalViewer(pngUrl: String) {
        Modal {
            open = true
            // NB: MUI Modal's onClose fires for both backdrop click and Escape (reason
            //     argument distinguishes them). We treat both the same: dismiss.
            onClose = { _, _ -> onModalClose() }

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
                    // NB: cursor pointer signals the backdrop is clickable to close.
                    cursor = Cursor.pointer
                }
                onClick = { onModalClose() }

                img {
                    src = pngUrl

                    css {
                        display = Display.block
                        // NB: fit-to-viewport — the largest size that fits while preserving
                        //     aspect ratio. Avoids the "user sees a fragment of a large image
                        //     pixel-for-pixel" effect of a no-constraint native-resolution
                        //     rendering. The browser does high-quality downscaling here.
                        maxWidth = 100.vw
                        maxHeight = 100.vh
                        cursor = Cursor.default
                    }
                    // NB: image clicks must not propagate to the backdrop's close handler.
                    onClick = { event -> event.stopPropagation() }
                }
            }
        }
    }
}
