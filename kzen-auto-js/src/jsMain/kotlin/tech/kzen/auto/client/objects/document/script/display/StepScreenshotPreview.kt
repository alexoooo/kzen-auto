package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreContext
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.platform.IoUtils
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface StepScreenshotPreviewProps: Props {
    var objectLocation: ObjectLocation
}


external interface StepScreenshotPreviewState: State {
    var screenshot: BinaryExecutionValue?
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState) {
        val traceInfo = computeStepTraceInfo(scriptState, props.objectLocation)
        val next = traceInfo.trace?.detail as? BinaryExecutionValue

        if (state.screenshot === next) {
            return
        }

        setState {
            this.screenshot = next
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

        div {
            css {
                marginLeft = 1.em
                flexShrink = number(0.0)
                padding = Padding(0.25.em, 0.25.em, 0.25.em, 0.25.em)

                // NB: drive the hover-zoom from the wrapper (whose layout box never moves)
                //     rather than from the <img> (which translates 11em off on hover and would
                //     lose contact with the cursor, causing infinite expand/shrink ping-pong).
                //     CSS :hover propagates up the DOM, so cursor over the wrapper OR over the
                //     transformed <img> descendant keeps wrapper:hover true. translateX shifts
                //     the expanded image past the thumbnail column (max 10em wide) so neighbours
                //     stay clickable; scale(5) keeps the popped-out preview large enough to read.
                "&:hover > img" {
                    transform = "translateX(11em) scale(5)".unsafeCast<Transform>()
                    zIndex = integer(50)
                }
            }

            img {
                css {
                    display = Display.block
                    // NB: resting size sized to a Step row with trace (~5em tall after icon +
                    //     paddings + small trace value); 10em wide cap accommodates typical 16:9
                    //     browser screenshots (8.9em at 5em tall) plus headroom.
                    maxWidth = 10.em
                    maxHeight = 5.em

                    // NB: visible boundary so the screenshot doesn't blend into surrounding white.
                    //     Border is included at both rest and hover (same value, no transition).
                    border = Border(1.px, LineStyle.solid, Color("rgba(0, 0, 0, 0.2)"))

                    // NB: position: relative is required for z-index to apply on hover (the
                    //     transform on hover creates a stacking context, but relative makes the
                    //     intent explicit and consistent at rest too).
                    position = Position.relative

                    // NB: top-left anchor — scale grows down and right only, never upward, so the
                    //     row above the hovered thumbnail is never visually covered.
                    transformOrigin = TransformOrigin(GeometryPosition.left, GeometryPosition.top)

                    // NB: animate only the `transform` property (not `z-index` — discrete lift).
                    transition = "transform 100ms ease-out".unsafeCast<Transition>()
                }
                src = screenshotPngUrl
            }
        }
    }
}
