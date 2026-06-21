package tech.kzen.auto.client.objects.document.script.display.image

import emotion.react.css
import js.objects.unsafeJso
import kotlinx.browser.window
import mui.material.IconButton
import mui.material.Modal
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


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
// reading order. A header names the step and Left/Right keys navigate. Mounted (by StepImageThumbnail or
// ScreenshotThumbnail) only while open.
//
// It reads the current trace snapshot synchronously from the ScriptStore (via context) and the current
// client state from the global, rebuilding the page sequence (pageScreenshots) on each render, so a
// neighbour resolves on demand and a live run's new frames appear without captured-state fields. The
// shown screenshot follows `currentKey` (changed by prev/next); re-renders are driven by navigation and
// by the originating thumbnail re-rendering.
class StepImageFullscreen(
    props: StepImageFullscreenProps
):
    RPureComponent<StepImageFullscreenProps, StepImageFullscreenState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
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
    }


    override fun componentWillUnmount() {
        window.removeEventListener("keydown", onKeyDown)
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
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center
                    backgroundColor = Color("rgba(0, 0, 0, 0.92)")
                    // NB: cursor pointer signals the backdrop is clickable to close.
                    cursor = Cursor.pointer
                }
                onClick = { props.onClose() }

                renderHeader(entry.title, canNavigate = entries.size > 1)

                renderNavButton(alignLeft = true, iconName = "NavigateBefore", enabled = hasPrev) {
                    navigate(-1)
                }

                img {
                    src = pngUrl(entry.image)

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

                renderNavButton(alignLeft = false, iconName = "NavigateNext", enabled = hasNext) {
                    navigate(1)
                }
            }
        }
    }


    // Prev/next button pinned to a viewport-height column on one edge, vertically centred. The
    // button stops click propagation so navigating doesn't also trigger the backdrop's close;
    // clicking the empty column still falls through to close ("click anywhere closes"). Disabled
    // (greyed) at the ends of the sequence.
    private fun ChildrenBuilder.renderNavButton(
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
                icon(iconName) {
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
