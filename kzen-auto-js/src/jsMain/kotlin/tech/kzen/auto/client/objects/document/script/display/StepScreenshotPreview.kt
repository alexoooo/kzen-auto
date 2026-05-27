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
                padding = Padding(0.5.em, 0.5.em, 0.5.em, 0.5.em)
            }

            img {
                css {
                    display = Display.block
                    maxWidth = 24.em
                    maxHeight = 10.em
                }
                src = screenshotPngUrl
            }
        }
    }
}
