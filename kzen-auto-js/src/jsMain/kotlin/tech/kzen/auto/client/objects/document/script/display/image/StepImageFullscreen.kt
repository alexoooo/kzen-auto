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
import tech.kzen.auto.client.objects.document.script.display.computeStepHeaderInfo
import tech.kzen.auto.client.objects.document.script.display.computeStepTraceInfo
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface StepImageFullscreenProps: Props {
    var initialLocation: ObjectLocation
    var onClose: () -> Unit
    var objectStableMapper: ObjectStableMapper
    var clientStateGlobal: ClientStateGlobal
}


external interface StepImageFullscreenState: State {
    // Which step's screenshot the full-screen view currently shows; navigation moves it between
    // steps while this component stays mounted.
    var location: ObjectLocation
}


//---------------------------------------------------------------------------------------------------------------------
// Full-screen image viewer ("lightbox") for step screenshots: a dark-backdrop MUI Modal with
// prev/next navigation across every screenshot-bearing step in the document, a header naming the
// step, and Left/Right keyboard control. Mounted by StepImageThumbnail only while open.
//
// It reads the current trace snapshot synchronously from the ScriptStore (via context) and the
// current client state from the global, so navigating to a neighbour resolves that step's
// screenshot/title on demand — no per-step observation, no captured-state fields. Re-renders are
// driven solely by `location` (navigation) and the originating thumbnail re-rendering.
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
        location = props.initialLocation
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


    // Step locations that currently have a screenshot, in document order — the navigation sequence.
    // Navigation spans the initialLocation's OWN document (which may be a sub-script opened from a
    // RunStep thumbnail), not the current ScriptStore document — so build that document's tree from
    // the global graph rather than reading scriptState.scriptTree (the current document only).
    private fun screenshotLocations(scriptState: ScriptState): List<ObjectLocation> {
        val documentPath = props.initialLocation.documentPath
        val graphDefinition = props.clientStateGlobal.current()?.graphDefinitionAttempt?.successful()
            ?: return listOf()
        return ScriptTree
            .read(documentPath, graphDefinition)
            .orderedDescendantObjectPaths()
            .map { documentPath.toObjectLocation(it) }
            .filter { hasScreenshot(scriptState, it) }
    }


    private fun hasScreenshot(scriptState: ScriptState, location: ObjectLocation): Boolean {
        val trace = computeStepTraceInfo(scriptState, location, props.objectStableMapper).trace
        return trace?.detail is BinaryExecutionValue
    }


    private fun navigate(delta: Int) {
        val scriptState = scriptState()
            ?: return

        val locations = screenshotLocations(scriptState)
        val index = locations.indexOf(state.location)
        if (index < 0) {
            return
        }

        val nextIndex = index + delta
        if (nextIndex < 0 || nextIndex >= locations.size) {
            // Stop at the ends.
            return
        }

        setState {
            location = locations[nextIndex]
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        // The shown screenshot follows `location` (changed by prev/next), resolved from the latest
        // trace snapshot read synchronously from the store.
        val scriptState = scriptState()
            ?: return

        val location = state.location
        val screenshot = computeStepTraceInfo(scriptState, location, props.objectStableMapper)
            .trace?.detail as? BinaryExecutionValue
            ?: return

        val locations = screenshotLocations(scriptState)
        val index = locations.indexOf(location)
        val hasPrev = index > 0
        val hasNext = index in 0 until (locations.size - 1)

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

                renderHeader(location, canNavigate = locations.size > 1)

                renderNavButton(alignLeft = true, iconName = "NavigateBefore", enabled = hasPrev) {
                    navigate(-1)
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
    private fun ChildrenBuilder.renderHeader(location: ObjectLocation, canNavigate: Boolean) {
        // Title follows `location` (navigation moves between steps); resolved from the current
        // client state.
        val title = props.clientStateGlobal.current()
            ?.let { computeStepHeaderInfo(it, location)?.title }

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
                +"${location.documentPath.name.value} > ${title ?: ""}"
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
