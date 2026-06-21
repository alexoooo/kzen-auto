package tech.kzen.auto.client.objects.document.script.display.image

import emotion.react.css
import js.objects.unsafeJso
import kotlinx.browser.window
import mui.material.IconButton
import mui.material.Modal
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import react.ChildrenBuilder
import react.Key
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*
import web.html.HTMLImageElement
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface StepImageFullscreenProps: Props {
    // Key (see PageScreenshotEntry) of the thumbnail the viewer was opened from — the starting position
    // in the page screenshot sequence.
    var initialKey: String
    var onClose: () -> Unit
    var objectStableMapper: ObjectStableMapper
    var clientStateGlobal: ClientStateGlobal
}


external interface StepImageFullscreenState: State {
    // Key of the screenshot currently shown; prev/next moves it along the page sequence while this
    // component stays mounted.
    var currentKey: String
}


//---------------------------------------------------------------------------------------------------------------------
// Full-screen image viewer ("lightbox") for step screenshots: a dark-backdrop MUI Modal with prev/next
// navigation across every screenshot the user sees on the current Script page — each step's right-of-step
// thumbnail plus, for an expanded RunStep, its detail film strip (the sub-script screenshots) — in page
// reading order. A header names the step, Left/Right keys (or the edge buttons) step through them, and a
// thumbnail timeline along the bottom lets the user jump directly to any screenshot. Mounted (by
// StepImageThumbnail or ScreenshotThumbnail) only while open.
//
// It reads the current trace snapshot synchronously from the ScriptStore (via context) and the current
// client state from the global, rebuilding the page sequence (pageScreenshots) on each render, so a
// neighbour resolves on demand and a live run's new frames appear without captured-state fields. The
// shown screenshot follows `currentKey` (changed by prev/next or a timeline click); re-renders are
// driven by navigation and by the originating thumbnail re-rendering.
class StepImageFullscreen(
    props: StepImageFullscreenProps
):
    RPureComponent<StepImageFullscreenProps, StepImageFullscreenState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Active-thumbnail accent; matches the highlight used by the in-page thumbnails.
        private val highlightColour = Color("#1565ff")
        private val timelineThumbBorder = Color("rgba(255, 255, 255, 0.35)")
        private val timelineBackground = Color("rgba(0, 0, 0, 0.55)")
        private val timelineBorder = Color("rgba(255, 255, 255, 0.15)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The timeline thumbnail of the currently-shown image, so navigation keeps it scrolled into view in
    // the horizontally-scrolling strip. Re-attached each render to whichever thumbnail is current.
    private val currentTimelineThumb: RefObject<HTMLImageElement> = createRef()


    // Stable handler so add/removeEventListener pair up. Handles Left/Right only (MUI Modal already
    // dismisses on Escape via onClose).
    private val onKeyDown: (Event?) -> Unit = { event ->
        when ((event as? KeyboardEvent)?.key) {
            "ArrowLeft" -> navigate(-1)
            "ArrowRight" -> navigate(1)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepImageFullscreenState.init(props: StepImageFullscreenProps) {
        currentKey = props.initialKey
    }


    override fun componentDidMount() {
        // The viewer exists only while open, so the listener's lifetime is simply its mount lifetime.
        window.addEventListener("keydown", onKeyDown)
        // Opened from a thumbnail deep in the strip → bring it into view immediately.
        scrollCurrentThumbIntoView()
    }


    override fun componentWillUnmount() {
        window.removeEventListener("keydown", onKeyDown)
    }


    override fun componentDidUpdate(
        prevProps: StepImageFullscreenProps,
        prevState: StepImageFullscreenState,
        snapshot: Any
    ) {
        // Follow the selection along the timeline after prev/next or a timeline click.
        if (prevState.currentKey != state.currentKey) {
            scrollCurrentThumbIntoView()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scrollCurrentThumbIntoView() {
        val element = currentTimelineThumb.current
            ?: return
        // Keep the active thumbnail centred in the strip; block:nearest avoids nudging the page
        // vertically. Built as a plain JS options object — the typed scrollIntoView overload isn't
        // reliably exposed across wrapper versions.
        element.asDynamic().scrollIntoView(js("({ block: 'nearest', inline: 'center' })"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scriptState(): ScriptState? =
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)?.stateOrNull()


    // The page's visible screenshots in reading order; empty until client state is available.
    private fun pageEntries(scriptState: ScriptState): List<PageScreenshotEntry> {
        val clientState = props.clientStateGlobal.current()
            ?: return listOf()
        return pageScreenshots(scriptState, clientState, props.objectStableMapper)
    }


    private fun navigate(delta: Int) {
        val scriptState = scriptState()
            ?: return

        val entries = pageEntries(scriptState)
        val index = entries.indexOfFirst { it.key == state.currentKey }
        if (index < 0) {
            return
        }

        val nextIndex = index + delta
        if (nextIndex < 0 || nextIndex >= entries.size) {
            // Stop at the ends.
            return
        }

        setState {
            currentKey = entries[nextIndex].key
        }
    }


    private fun navigateTo(key: String) {
        if (key != state.currentKey) {
            setState {
                currentKey = key
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val scriptState = scriptState()
            ?: return

        val entries = pageEntries(scriptState)
        val index = entries.indexOfFirst { it.key == state.currentKey }
        val entry = entries.getOrNull(index)
            ?: return

        val hasPrev = index > 0
        val hasNext = index < entries.size - 1
        val showTimeline = entries.size > 1

        Modal {
            open = true
            // NB: MUI Modal's onClose fires for both backdrop click and Escape (reason
            //     argument distinguishes them). We treat both the same: dismiss.
            onClose = { _, _ -> props.onClose() }

            div {
                css {
                    position = Position.fixed
                    top = 0.px
                    left = 0.px
                    width = 100.vw
                    height = 100.vh
                    display = Display.flex
                    // Column so the timeline takes the bottom and the image fills the rest — a tall
                    // image is bounded above the strip instead of hiding behind it.
                    flexDirection = FlexDirection.column
                    backgroundColor = Color("rgba(0, 0, 0, 0.92)")
                    // NB: cursor pointer signals the backdrop is clickable to close.
                    cursor = Cursor.pointer
                }
                onClick = { props.onClose() }

                renderHeader(entry.title, canNavigate = showTimeline)

                // Image area: fills the space above the timeline; the edge nav buttons pin to it.
                div {
                    css {
                        position = Position.relative
                        flexGrow = number(1.0)
                        flexShrink = number(1.0)
                        // NB: lets this flex child shrink below the image's intrinsic height so
                        //     maxHeight = 100% bounds the image to the area above the timeline.
                        minHeight = 0.px
                        display = Display.flex
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                    }

                    renderNavButton(alignLeft = true, iconName = "NavigateBefore", enabled = hasPrev) {
                        navigate(-1)
                    }

                    img {
                        src = pngUrl(entry.image)

                        css {
                            display = Display.block
                            // NB: fit-to-area — the largest size that fits the image area while
                            //     preserving aspect ratio. Avoids the "user sees a fragment of a
                            //     large image pixel-for-pixel" effect of native-resolution rendering.
                            //     The browser does high-quality downscaling here.
                            maxWidth = 100.pct
                            maxHeight = 100.pct
                            // NB: clicking the image closes too ("click anywhere") — no onClick here, so
                            //     the click bubbles to the backdrop div's close handler.
                            cursor = Cursor.pointer
                        }
                    }

                    renderNavButton(alignLeft = false, iconName = "NavigateNext", enabled = hasNext) {
                        navigate(1)
                    }
                }

                if (showTimeline) {
                    renderTimeline(entries, index)
                }
            }
        }
    }


    // Prev/next button pinned to one edge of the image area, vertically centred. The button stops click
    // propagation so navigating doesn't also trigger the backdrop's close; clicking the empty column
    // still falls through to close ("click anywhere closes"). Disabled (greyed) at the ends.
    private fun ChildrenBuilder.renderNavButton(
        alignLeft: Boolean,
        iconName: String,
        enabled: Boolean,
        onActivate: () -> Unit
    ) {
        div {
            css {
                position = Position.absolute
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
                icon(iconName) {
                    style = unsafeJso {
                        color = if (enabled) NamedColor.white else Color("rgba(255, 255, 255, 0.3)")
                        fontSize = 2.5.em
                    }
                }
            }
        }
    }


    // The bottom timeline: a scrub slider over a horizontally-scrolling strip of every page screenshot
    // in reading order, the current one highlighted, so the user can flip rapidly through them (drag the
    // slider for a flipbook effect) or jump straight to any image (click a thumbnail). A control surface,
    // not a dismiss target — clicks on it (gaps included) are swallowed so they don't close the viewer.
    private fun ChildrenBuilder.renderTimeline(
        entries: List<PageScreenshotEntry>,
        currentIndex: Int
    ) {
        div {
            css {
                flexShrink = number(0.0)
                display = Display.flex
                flexDirection = FlexDirection.column
                boxSizing = BoxSizing.borderBox
                padding = Padding(0.5.em, 1.em, 0.5.em, 1.em)
                backgroundColor = timelineBackground
                borderTop = Border(1.px, LineStyle.solid, timelineBorder)
                cursor = Cursor.default
            }
            onClick = { it.stopPropagation() }

            renderScrubber(entries, currentIndex)
            renderThumbnailStrip(entries, currentIndex)
        }
    }


    // A range slider spanning the whole sequence: drag it to flip rapidly through the frames (flipbook).
    // Controlled by the current index, so it tracks arrow-key and thumbnail navigation too.
    private fun ChildrenBuilder.renderScrubber(
        entries: List<PageScreenshotEntry>,
        currentIndex: Int
    ) {
        input {
            value = currentIndex.toString()
            // NB: type/min/max/step set via asDynamic — the range InputType member and the numeric range
            //     attributes aren't reliably typed on the native input across wrapper versions; the DOM
            //     accepts these string values.
            asDynamic().type = "range"
            asDynamic().min = "0"
            asDynamic().max = "${entries.size - 1}"
            asDynamic().step = "1"

            css {
                width = 100.pct
                marginBottom = 0.5.em
                cursor = Cursor.pointer
            }

            onChange = { event ->
                val nextIndex = (event.target as HTMLInputElement).value.toIntOrNull()
                if (nextIndex != null) {
                    entries.getOrNull(nextIndex)?.let { navigateTo(it.key) }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderThumbnailStrip(
        entries: List<PageScreenshotEntry>,
        currentIndex: Int
    ) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                overflowX = Auto.auto
                overflowY = Overflow.hidden
            }

            entries.forEachIndexed { entryIndex, entry ->
                val current = entryIndex == currentIndex

                img {
                    key = Key(entry.key)
                    if (current) {
                        ref = currentTimelineThumb
                    }
                    src = pngUrl(entry.image)

                    css {
                        flexShrink = number(0.0)
                        height = 3.5.em
                        display = Display.block
                        marginRight = 0.5.em
                        cursor = Cursor.pointer
                        // NB: 2px border at both states — only the colour changes — so highlighting
                        //     the current thumbnail doesn't shift the strip's layout.
                        border = Border(
                            2.px,
                            LineStyle.solid,
                            if (current) highlightColour else timelineThumbBorder)
                        transition = "border-color 120ms ease-out".unsafeCast<Transition>()
                    }

                    onClick = { event ->
                        event.stopPropagation()
                        navigateTo(entry.key)
                    }
                }
            }
        }
    }


    // Indicator so the full-screen view is self-explanatory (e.g. if opened as a browser tab):
    // which Script / Step it belongs to, and how to dismiss. pointer-events none → clicking the
    // bar still falls through to the backdrop's close handler ("click anywhere closes").
    private fun ChildrenBuilder.renderHeader(title: String, canNavigate: Boolean) {
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
                +title
            }

            div {
                css {
                    fontSize = 0.85.em
                    opacity = number(0.8)
                    marginTop = 0.25.em
                }

                if (canNavigate) {
                    +"Use ← → to navigate · click anywhere or press Esc to close"
                }
                else {
                    +"Click anywhere or press Esc to close"
                }
            }
        }
    }
}
