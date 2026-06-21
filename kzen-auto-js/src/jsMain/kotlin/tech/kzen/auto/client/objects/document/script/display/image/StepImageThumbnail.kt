package tech.kzen.auto.client.objects.document.script.display.image

import emotion.react.css
import kotlinx.browser.document
import kotlinx.browser.window
import react.ChildrenBuilder
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.script.display.computeStepTraceInfo
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*
import web.html.HTMLImageElement


//---------------------------------------------------------------------------------------------------------------------
external interface StepImageThumbnailProps: Props {
    var objectLocation: ObjectLocation
    var objectStableMapper: ObjectStableMapper
    var clientStateGlobal: ClientStateGlobal
}


external interface StepImageThumbnailState: State {
    var screenshot: BinaryExecutionValue?

    // For a RunStep only: the frame a hovered strip thumbnail is requesting (via ScriptState). When
    // non-null it overrides what the floating preview shows — the small thumbnail keeps showing
    // `screenshot` (the latest), only the large preview tracks the hover.
    var previewScreenshot: BinaryExecutionValue?

    var hovered: Boolean
    var expanded: Boolean
    var floatingTop: Double
    var floatingLeft: Double

    var fullscreenOpen: Boolean

    // Page-sequence key the full-screen viewer opens on (see PageScreenshotEntry): a RunStep opens on
    // its representative frame, which coincides with one of the strip frames; any other step on its own.
    var openKey: String
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

    // A persistent (expanded) preview is position:fixed, anchored to the thumbnail's viewport rect.
    // While it's shown, re-measure that rect every animation frame so the preview follows the thumbnail
    // through ANY layout shift — page/panel scroll, window resize, AND reflows that fire no scroll or
    // resize event (notably the sidebar collapsing/expanding, which slides the thumbnail sideways).
    // recomputeFloatingPosition is guarded, so an idle frame costs one getBoundingClientRect and no
    // setState. Active only while expanded (the only time the preview is persistent).
    private var positionLoopActive = false


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        val scriptStore = contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)
        // ScriptStore.observe replays onScriptState synchronously with the current state, which
        // carries the step's expansion — so a thumbnail (re)mounting while its step is already
        // expanded is handled there (attaches viewport listeners; componentDidUpdate then positions).
        scriptStore?.observe(this)
    }


    override fun componentWillUnmount() {
        val scriptStore = contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)
        scriptStore?.unobserve(this)
        stopPositionLoop()
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
        previewScreenshot = null
        hovered = false
        expanded = false
        floatingTop = 0.0
        floatingLeft = 0.0
        fullscreenOpen = false
        openKey = ""
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState) {
        // For a RunStep, the representative is the latest screenshot anywhere in its subtree, resolved
        // by ScriptProgressStore and keyed by stable id; show that frame directly. For any other step
        // there's no entry, so fall back to the step's own latest frame from the trace snapshot.
        val runStepStableId = props.objectStableMapper.objectStableId(props.objectLocation)
        val representative = scriptState.progress.representativeFrame(runStepStableId)
        val nextScreenshot: BinaryExecutionValue? =
            (representative?.value as? BinaryExecutionValue)
                ?: computeStepTraceInfo(scriptState, props.objectLocation, props.objectStableMapper)
                    .trace?.detail as? BinaryExecutionValue

        // A RunStep opens full-screen on its representative frame (which is one of its strip frames when
        // expanded); any other step on its own page entry.
        val nextOpenKey =
            if (representative != null) {
                PageScreenshotEntry.frameKey(representative.sequence)
            }
            else {
                PageScreenshotEntry.stepKey(props.objectLocation)
            }

        val nextExpanded = scriptState.isStepExpanded(props.objectLocation)

        // A hovered detail-strip frame requests a specific frame via ScriptState; show it in the
        // floating preview only (the small thumbnail stays on the latest representative). Always null
        // for non-RunSteps (nobody sets their key).
        val nextPreviewScreenshot = scriptState.hoveredScreenshot(props.objectLocation)

        val screenshotChanged = state.screenshot !== nextScreenshot
        val expandedChanged = state.expanded != nextExpanded
        val previewChanged = state.previewScreenshot !== nextPreviewScreenshot
        val openKeyChanged = state.openKey != nextOpenKey
        if (!screenshotChanged && !expandedChanged && !previewChanged && !openKeyChanged) {
            return
        }

        // Keep the persistent (expanded) preview glued to its thumbnail — run the per-frame position
        // tracker only while expanded (start on the false→true transition, stop on true→false).
        if (expandedChanged) {
            if (nextExpanded) {
                startPositionLoop()
            }
            else {
                stopPositionLoop()
            }
        }

        setState {
            this.screenshot = nextScreenshot
            this.expanded = nextExpanded
            this.previewScreenshot = nextPreviewScreenshot
            this.openKey = nextOpenKey
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun startPositionLoop() {
        if (positionLoopActive) {
            return
        }
        positionLoopActive = true
        schedulePositionFrame()
    }


    private fun stopPositionLoop() {
        positionLoopActive = false
    }


    private fun schedulePositionFrame() {
        window.requestAnimationFrame {
            if (!positionLoopActive) {
                return@requestAnimationFrame
            }
            recomputeFloatingPosition()
            schedulePositionFrame()
        }
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

        // The app header is a fixed band at the top of the viewport that body content scrolls under;
        // clamp the preview below the header's live bottom edge (not the raw viewport top, which let the
        // preview slide behind the header when the thumbnail scrolled up under it). coerceAtLeast last so
        // a too-short viewport clips the bottom rather than hiding the preview behind the header.
        val minTop = appHeaderBottomPx() + GAP_PX
        val top = rect.top
            .coerceAtMost(vh - PREVIEW_MAX_H_PX - GAP_PX)
            .coerceAtLeast(minTop)

        return left to top
    }


    // Bottom edge (viewport px) of the fixed app header, measured live so a taller header (ribbon tab /
    // raw-view changes its height) is respected; 0 if it isn't in the DOM. The header carries the
    // data-app-header marker set in ProjectController.
    private fun appHeaderBottomPx(): Double {
        val header = document.querySelector("[data-app-header]")
            ?: return 0.0
        return header.getBoundingClientRect().bottom
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

        // The floating preview shows on hover AND while the step is expanded (a persistent "as if
        // hovered" view to the right; expansion arrives via onScriptState). A hovered detail-strip
        // frame overrides the shown frame via previewScreenshot; the small thumbnail stays on the
        // latest representative (screenshot).
        if (state.hovered || state.expanded) {
            val previewPngUrl = state.previewScreenshot?.let { pngUrl(it) } ?: screenshotPngUrl
            renderFloatingPreview(previewPngUrl)
        }

        if (state.fullscreenOpen) {
            StepImageFullscreen::class.react {
                // Open on this thumbnail's entry; left/right then walks the whole page in reading order.
                initialKey = state.openKey
                onClose = { onFullscreenClose() }
                objectStableMapper = props.objectStableMapper
                clientStateGlobal = props.clientStateGlobal
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
