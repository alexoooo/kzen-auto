package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import js.objects.unsafeJso
import kotlinx.browser.window
import mui.material.IconButton
import mui.material.Modal
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
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
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.material.iconByName
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
    var expanded: Boolean
    var floatingTop: Double
    var floatingLeft: Double

    var stepTitle: String?

    var modalOpen: Boolean

    // Which step's screenshot the full-screen view currently shows; navigation moves it between
    // steps while the originating component's modal stays mounted.
    var modalLocation: ObjectLocation?
}


//---------------------------------------------------------------------------------------------------------------------
// TODO: split this into thumbnail/preview/fullscreen, consolidate StepImageDisplay related code into package
@Suppress("unused")
class StepScreenshotPreview(
    props: StepScreenshotPreviewProps
):
    RPureComponent<StepScreenshotPreviewProps, StepScreenshotPreviewState>(props),
    ScriptStore.Observer,
    ClientStateGlobal.Observer
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

    // Latest state captured from the observers (kept current even when the per-step early-returns
    // skip setState) so the full-screen view can resolve OTHER steps' screenshots and titles for
    // prev/next navigation. Plain fields, not React state — navigation re-renders via modalLocation.
    private var latestScriptState: ScriptState? = null
    private var latestClientState: ClientState? = null

    // Stable handler so add/removeEventListener pair up. Active only while the full-screen view is
    // open; handles Left/Right only (MUI Modal already dismisses on Escape via onClose).
    private val onModalKeyDown: (Event?) -> Unit = { event ->
        when ((event as? KeyboardEvent)?.key) {
            "ArrowLeft" -> navigateModal(-1)
            "ArrowRight" -> navigateModal(1)
        }
    }
    private var modalKeyListenerAttached = false


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(ScriptStoreContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        val scriptStore = contextValue<ScriptStore?>()
        // ScriptStore.observe replays onScriptState synchronously with the current state, which
        // carries the step's expansion — so a preview (re)mounting while its step is already
        // expanded is handled there (attaches viewport listeners; componentDidUpdate then positions).
        scriptStore?.observe(this)
        ClientContext.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        val scriptStore = contextValue<ScriptStore?>()
        scriptStore?.unobserve(this)
        ClientContext.clientStateGlobal.unobserve(this)
        detachViewportListeners()
        detachModalKeyListener()
    }


    override fun componentDidUpdate(
        prevProps: StepScreenshotPreviewProps,
        prevState: StepScreenshotPreviewState,
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
    override fun StepScreenshotPreviewState.init(props: StepScreenshotPreviewProps) {
        screenshot = null
        hovered = false
        expanded = false
        floatingTop = 0.0
        floatingLeft = 0.0
        stepTitle = null
        modalOpen = false
        modalLocation = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState) {
        // Captured before the early-return below so the full-screen view's navigation always sees
        // the current trace snapshot, even when this step's own screenshot/expansion is unchanged.
        latestScriptState = scriptState

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


    override fun onClientState(clientState: ClientState) {
        // Captured before the early-return below so navigation can resolve other steps' titles.
        latestClientState = clientState

        val headerInfo = computeStepHeaderInfo(clientState, props.objectLocation)
            ?: return

        if (state.stepTitle == headerInfo.title) {
            return
        }

        setState {
            this.stepTitle = headerInfo.title
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
    private fun attachModalKeyListener() {
        if (modalKeyListenerAttached) {
            return
        }
        modalKeyListenerAttached = true
        window.addEventListener("keydown", onModalKeyDown)
    }


    private fun detachModalKeyListener() {
        if (!modalKeyListenerAttached) {
            return
        }
        modalKeyListenerAttached = false
        window.removeEventListener("keydown", onModalKeyDown)
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
        attachModalKeyListener()
        setState {
            modalOpen = true
            modalLocation = props.objectLocation
        }
    }


    private fun onModalClose() {
        detachModalKeyListener()
        setState {
            modalOpen = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Step locations that currently have a screenshot, in document order — the navigation sequence
    // for the full-screen view.
    private fun screenshotLocations(): List<ObjectLocation> {
        val scriptState = latestScriptState
            ?: return emptyList()

        val documentPath = props.objectLocation.documentPath
        return scriptState.scriptTree
            .orderedDescendantObjectPaths()
            .map { documentPath.toObjectLocation(it) }
            .filter { hasScreenshot(scriptState, it) }
    }


    private fun hasScreenshot(scriptState: ScriptState, location: ObjectLocation): Boolean {
        val trace = computeStepTraceInfo(scriptState, location, ClientContext.objectStableMapper).trace
        return trace?.detail is BinaryExecutionValue
    }


    private fun navigateModal(delta: Int) {
        val locations = screenshotLocations()
        val index = locations.indexOf(state.modalLocation ?: props.objectLocation)
        if (index < 0) {
            return
        }

        val nextIndex = index + delta
        if (nextIndex < 0 || nextIndex >= locations.size) {
            // Stop at the ends.
            return
        }

        setState {
            modalLocation = locations[nextIndex]
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

        if (state.modalOpen) {
            renderModalViewer()
        }
    }


    // base64 data URL, cached on the screenshot value (shared across thumbnail, floating preview,
    // and the full-screen view, including navigated-to neighbours).
    private fun pngUrl(screenshot: BinaryExecutionValue): String =
        screenshot.cache("img") {
            val base64 = IoUtils.base64Encode(screenshot.value)
            "data:png/png;base64,$base64"
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderModalViewer() {
        // The shown screenshot follows modalLocation (changed by prev/next), resolved from the
        // latest trace snapshot; fall back to this step's own screenshot if the snapshot is absent.
        val modalLocation = state.modalLocation
            ?: props.objectLocation

        val screenshot = latestScriptState
            ?.let { computeStepTraceInfo(it, modalLocation, ClientContext.objectStableMapper).trace?.detail as? BinaryExecutionValue }
            ?: state.screenshot
            ?: return

        val locations = screenshotLocations()
        val index = locations.indexOf(modalLocation)
        val hasPrev = index > 0
        val hasNext = index in 0 until (locations.size - 1)

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

                renderModalHeader(modalLocation, canNavigate = locations.size > 1)

                renderModalNavButton(alignLeft = true, iconName = "NavigateBefore", enabled = hasPrev) {
                    navigateModal(-1)
                }

                img {
                    src = pngUrl(screenshot)

                    css {
                        display = Display.block
                        // NB: fit-to-viewport — the largest size that fits while preserving
                        //     aspect ratio. Avoids the "user sees a fragment of a large image
                        //     pixel-for-pixel" effect of a no-constraint native-resolution
                        //     rendering. The browser does high-quality downscaling here.
                        maxWidth = 100.vw
                        maxHeight = 100.vh
                        // NB: clicking the image closes too ("click anywhere") — no onClick here, so
                        //     the click bubbles to the backdrop div's close handler.
                        cursor = Cursor.pointer
                    }
                }

                renderModalNavButton(alignLeft = false, iconName = "NavigateNext", enabled = hasNext) {
                    navigateModal(1)
                }
            }
        }
    }


    // Prev/next button pinned to a viewport-height column on one edge, vertically centred. The
    // button stops click propagation so navigating doesn't also trigger the backdrop's close;
    // clicking the empty column still falls through to close ("click anywhere closes"). Disabled
    // (greyed) at the ends of the sequence.
    private fun ChildrenBuilder.renderModalNavButton(
        alignLeft: Boolean,
        iconName: String,
        enabled: Boolean,
        onActivate: () -> Unit
    ) {
        div {
            css {
                position = Position.fixed
                top = 0.px
                bottom = 0.px
                if (alignLeft) {
                    left = 0.px
                }
                else {
                    right = 0.px
                }
                display = Display.flex
                alignItems = AlignItems.center
                padding = Padding(0.px, 0.5.em, 0.px, 0.5.em)
                zIndex = integer(2)
            }

            IconButton {
                disabled = !enabled
                onClick = { event ->
                    event.stopPropagation()
                    onActivate()
                }
                iconByName(iconName) {
                    style = unsafeJso {
                        color = if (enabled) NamedColor.white else Color("rgba(255, 255, 255, 0.3)")
                        fontSize = 2.5.em
                    }
                }
            }
        }
    }


    // Indicator so the full-screen view is self-explanatory (e.g. if opened as a browser tab):
    // which Script / Step it belongs to, and how to dismiss. pointer-events none → clicking the
    // bar still falls through to the backdrop's close handler ("click anywhere closes").
    private fun ChildrenBuilder.renderModalHeader(modalLocation: ObjectLocation, canNavigate: Boolean) {
        // Title follows modalLocation (navigation moves between steps); resolved from the latest
        // client state, falling back to this step's own title.
        val title = latestClientState
            ?.let { computeStepHeaderInfo(it, modalLocation)?.title }
            ?: state.stepTitle

        div {
            css {
                position = Position.fixed
                top = 0.px
                left = 0.px
                width = 100.vw
                boxSizing = BoxSizing.borderBox
                padding = Padding(0.75.em, 1.em, 0.75.em, 1.em)
                zIndex = integer(1)
                color = NamedColor.white
                textAlign = TextAlign.center
                // Scrim so the label stays legible over a bright image top.
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
                +"${modalLocation.documentPath.name.value} > ${title ?: ""}"
            }

            div {
                css {
                    fontSize = 0.85.em
                    opacity = number(0.8)
                    marginTop = 0.25.em
                }
                +if (canNavigate) {
                    "Use ← → to navigate · click anywhere or press Esc to close"
                }
                else {
                    "Click anywhere or press Esc to close"
                }
            }
        }
    }
}
