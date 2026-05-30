package tech.kzen.auto.client.objects.document.script.display.image

import emotion.react.css
import kotlinx.browser.window
import org.w3c.dom.events.Event
import react.ChildrenBuilder
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.objects.document.script.display.computeStepTraceInfo
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreContext
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*
import web.html.HTMLImageElement


//---------------------------------------------------------------------------------------------------------------------
external interface StepImageThumbnailProps: Props {
    var objectLocation: ObjectLocation
}


external interface StepImageThumbnailState: State {
    var screenshot: BinaryExecutionValue?

    var hovered: Boolean
    var expanded: Boolean
    var floatingTop: Double
    var floatingLeft: Double

    var fullscreenOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// Per-step image widget: a thumbnail of the step's screenshot trace plus a floating preview shown
// on hover, or persistently while the step is expanded. Clicking opens the full-screen viewer.
// The thumbnail and floating preview are one DOM-coupled unit — the fixed-positioned preview is
// anchored to the thumbnail's bounding rect and they share hover/position state — so they live
// together here; only the genuinely independent full-screen view (StepImageFullscreen) is split out.
class StepImageThumbnail(
    props: StepImageThumbnailProps
):
    RPureComponent<StepImageThumbnailProps, StepImageThumbnailState>(props),
    ScriptStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val highlightColour = Color("#1565ff")
        private val restingBorderColour = Color("rgba(0, 0, 0, 0.4)")

        // NB: matches maxWidth/maxHeight on the floating preview; used to compute viewport-aware
        //     placement. emToPx assumes the default 16px root font.
        private const val EM_TO_PX = 16.0
        private const val PREVIEW_MAX_W_PX = 50 * EM_TO_PX  // 800
        private const val PREVIEW_MAX_H_PX = 25 * EM_TO_PX  // 400
        private const val GAP_PX = 8.0

        // Floating-preview stacking: hovering a thumbnail lifts its preview above other expanded
        // (and thus persistently visible) previews where they overlap.
        private const val FLOATING_Z_BASE = 100
        private const val FLOATING_Z_HOVERED = 101
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val imgRef: RefObject<HTMLImageElement> = createRef()

    // NB: stable handler reference so add/removeEventListener pair up. Active only while a preview is
    //     persistently shown (its step expanded) — keeps it glued to the thumbnail as the page or
    //     step-list scrolls/resizes (a fixed-positioned overlay otherwise detaches on scroll).
    private val onViewportChange: (Event?) -> Unit = { recomputeFloatingPosition() }
    private var viewportListenersAttached = false


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(ScriptStoreContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        val scriptStore = contextValue<ScriptStore?>()
        // ScriptStore.observe replays onScriptState synchronously with the current state, which
        // carries the step's expansion — so a thumbnail (re)mounting while its step is already
        // expanded is handled there (attaches viewport listeners; componentDidUpdate then positions).
        scriptStore?.observe(this)
    }


    override fun componentWillUnmount() {
        val scriptStore = contextValue<ScriptStore?>()
        scriptStore?.unobserve(this)
        detachViewportListeners()
    }


    override fun componentDidUpdate(
        prevProps: StepImageThumbnailProps,
        prevState: StepImageThumbnailState,
        snapshot: Any
    ) {
        // Keep a persistent (expanded) preview anchored after any re-render — e.g. once the
        // screenshot first arrives, or the row's layout shifts. recomputeFloatingPosition is guarded
        // against no-op churn, so this cannot loop.
        if (state.expanded) {
            recomputeFloatingPosition()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepImageThumbnailState.init(props: StepImageThumbnailProps) {
        screenshot = null
        hovered = false
        expanded = false
        floatingTop = 0.0
        floatingLeft = 0.0
        fullscreenOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState) {
        val traceInfo = computeStepTraceInfo(
            scriptState, props.objectLocation, ClientContext.objectStableMapper)
        val nextScreenshot = traceInfo.trace?.detail as? BinaryExecutionValue
        val nextExpanded = scriptState.isStepExpanded(props.objectLocation)

        val screenshotChanged = state.screenshot !== nextScreenshot
        val expandedChanged = state.expanded != nextExpanded
        if (!screenshotChanged && !expandedChanged) {
            return
        }

        // Keep the persistent (expanded) preview glued to its thumbnail across scroll/resize only
        // while expanded — attach on the false→true transition, detach on true→false.
        if (expandedChanged) {
            if (nextExpanded) {
                attachViewportListeners()
            }
            else {
                detachViewportListeners()
            }
        }

        setState {
            this.screenshot = nextScreenshot
            this.expanded = nextExpanded
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun attachViewportListeners() {
        if (viewportListenersAttached) {
            return
        }
        viewportListenersAttached = true
        // NB: capture phase ("scroll" doesn't bubble) so scrolling the inner step-list panel — not
        //     just the window — repositions the preview.
        window.addEventListener("scroll", onViewportChange, true)
        window.addEventListener("resize", onViewportChange)
    }


    private fun detachViewportListeners() {
        if (!viewportListenersAttached) {
            return
        }
        viewportListenersAttached = false
        window.removeEventListener("scroll", onViewportChange, true)
        window.removeEventListener("resize", onViewportChange)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Viewport-aware placement: always to the right of the thumbnail (no left-side fallback —
    // flipping left would put the preview under the sidebar on narrow windows; accept right-edge
    // clipping instead), top aligned with the thumbnail then clamped within the viewport.
    private fun floatingPosition(): Pair<Double, Double>? {
        val rect = imgRef.current?.getBoundingClientRect()
            ?: return null

        val vh = window.innerHeight.toDouble()
        val left = rect.right + GAP_PX
        val top = rect.top
            .coerceAtMost(vh - PREVIEW_MAX_H_PX - GAP_PX)
            .coerceAtLeast(GAP_PX)

        return left to top
    }


    private fun onThumbnailEnter() {
        val (left, top) = floatingPosition()
            ?: return

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


    // Hovering the (expanded) preview itself lifts it above overlapping neighbours — same z-index
    // bump as hovering the thumbnail. NB: unlike onThumbnailEnter this does NOT recompute placement
    // (the expanded preview is already pinned); it only toggles hovered. hovered=false on leave
    // doesn't hide the preview because render keeps it while state.expanded is true.
    private fun onPreviewEnter() {
        if (!state.hovered) {
            setState {
                hovered = true
            }
        }
    }


    private fun onPreviewLeave() {
        if (state.hovered) {
            setState {
                hovered = false
            }
        }
    }


    private fun recomputeFloatingPosition() {
        val (left, top) = floatingPosition()
            ?: return

        if (state.floatingLeft == left && state.floatingTop == top) {
            return
        }

        setState {
            floatingLeft = left
            floatingTop = top
        }
    }


    private fun onThumbnailClick() {
        setState {
            fullscreenOpen = true
        }
    }


    private fun onFullscreenClose() {
        setState {
            fullscreenOpen = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val screenshot = state.screenshot
            ?: return

        val screenshotPngUrl = pngUrl(screenshot)

        renderThumbnail(screenshotPngUrl)

        // Floating preview shows on hover AND while the step is expanded (a persistent "as if
        // hovered" view to the right; expansion arrives via onScriptState).
        if (state.hovered || state.expanded) {
            renderFloatingPreview(screenshotPngUrl)
        }

        if (state.fullscreenOpen) {
            StepImageFullscreen::class.react {
                initialLocation = props.objectLocation
                onClose = { onFullscreenClose() }
            }
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
                    // NB: constant drop shadow (independent of hover) lifts the thumbnail off the
                    //     gray page background for contrast.
                    boxShadow = BoxShadow(0.px, 1.px, 3.px, Color("rgba(0, 0, 0, 0.3)"))
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
        val persistent = state.expanded

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

                zIndex = integer(if (state.hovered) FLOATING_Z_HOVERED else FLOATING_Z_BASE)

                if (persistent) {
                    // Expanded → the preview is itself a click target for the full-screen view.
                    cursor = Cursor.pointer
                }
                else {
                    // NB: transient hover — cursor passes through the preview, so onMouseLeave on
                    //     the thumbnail fires the moment the cursor leaves it (clean dismissal).
                    pointerEvents = None.none
                }
            }

            if (persistent) {
                onClick = { onThumbnailClick() }
                // Hovering the preview (not just the thumbnail) brings it forward over overlapping
                // neighbours.
                onMouseEnter = { onPreviewEnter() }
                onMouseLeave = { onPreviewLeave() }
            }
        }
    }
}
