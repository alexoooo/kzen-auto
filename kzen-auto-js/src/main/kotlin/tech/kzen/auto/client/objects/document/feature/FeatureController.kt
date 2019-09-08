package tech.kzen.auto.client.objects.document.feature

import kotlinx.css.*
import react.*
import react.dom.br
import react.dom.img
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.platform.IoUtils


@Suppress("unused")
class FeatureController(
        props: Props
):
        RPureComponent<FeatureController.Props, FeatureController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val screenshotTakerLocation = ObjectLocation(
                DocumentPath.parse("auto-jvm/feature/feature.yaml"),
                ObjectPath(ObjectName("ScreenshotTaker"), ObjectNesting.root))
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props: RProps


    class State(
            var detail: CropperDetail?,
            var screenshotDataUrl: String?,
            var capturedDataUrl: String?,
            var requestingScreenshot: Boolean?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            private val archetype: ObjectLocation
    ):
            DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(FeatureController::class) {
                //                attrs {
//                    this.attributeController = this@Wrapper.attributeController
//                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var cropperWrapper: CropperWrapper? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        console.log("^^^^ State.init")
        detail = null
        screenshotDataUrl = null
        capturedDataUrl = null
        requestingScreenshot = false
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
//        console.log("^^^^ componentDidUpdate", state.requestingScreenshot, prevState.requestingScreenshot)
        if (state.screenshotDataUrl == null && state.requestingScreenshot != true) {
            setState {
                requestingScreenshot = true
            }
        }

        if (state.requestingScreenshot == true && prevState.requestingScreenshot != true) {
            doRequestScreenshot()
        }
    }


    override fun componentDidMount() {
//        console.log("^^^^^^ componentDidMount", state.requestingScreenshot)
        if (state.screenshotDataUrl == null) {
            setState {
                requestingScreenshot = true
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun doRequestScreenshot() {
        async {
//            console.log("doRequestScreenshot", screenshotTakerLocation.toString())
            val result= ClientContext.restClient.performDetached(
                    screenshotTakerLocation)

            if (result is ImperativeSuccess) {
                val screenshotPng = result.value as BinaryExecutionValue
                val base64 = IoUtils.base64Encode(screenshotPng.value)
                val screenshotPngUrl = "data:png/png;base64,$base64"

                setState {
                    screenshotDataUrl = screenshotPngUrl
                    requestingScreenshot = false
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCrop(detail: CropperDetail) {
        setState {
            this.detail = detail
        }
    }


    private fun onSave() {
        val detail = state.detail
                ?: return

        console.log("detail", detail)

        val canvas = cropperWrapper!!.getCroppedCanvas()

        console.log("canvas", canvas)

        setState {
            capturedDataUrl = canvas.toDataURL()

            requestingScreenshot = true
        }
    }


    private fun onCapture() {
        setState {
            capturedDataUrl = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                padding(1.em, 1.em, 0.px, 1.em)
            }

            val capturedDataUrl = state.capturedDataUrl
            val screenshotDataUrl = state.screenshotDataUrl
//                    ?: "screenshot.png"

//            +"requestingScreenshot: ${state.requestingScreenshot}"

            when {
                capturedDataUrl != null -> {
                    renderCapture()

                    br {}

                    renderDataUrl(capturedDataUrl)
                }

                screenshotDataUrl != null -> {
                    renderSave()

                    br {}

                    renderCropper(screenshotDataUrl)
                }

                else ->
                    +"<taking screenshot>"
            }
        }
    }


    private fun RBuilder.renderDataUrl(dataUrl: String) {
        img {
            attrs {
                src = dataUrl
            }
        }
    }


    private fun RBuilder.renderCapture() {
        styledDiv {
            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = ::onCapture

                    style = reactStyle {
                        backgroundColor = Color.white
                    }
                }

                child(CameraAltIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
                    }
                }
                +"Capture"
            }
        }
    }


    private fun RBuilder.renderSave() {
        styledDiv {
            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = ::onSave

                    style = reactStyle {
                        backgroundColor = Color.white
                    }
                }

                child(CameraAltIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
                    }
                }
                +"Save"
            }
        }
    }


    private fun RBuilder.renderCropper(
            screenshotDataUrl: String
    ) {
        styledDiv {
            css {
                width = 100.pct
                height = 100.vh.minus(9.em)
                minHeight = 200.px
                maxHeight = 1024.px
            }

            child(CropperWrapper::class) {
                attrs {
                    src = screenshotDataUrl

                    crop = {event ->
                        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
                        val detail = event.detail as CropperDetail
                        onCrop(detail)
                    }
                }

                ref {
                    cropperWrapper = it as? CropperWrapper
                }
            }
        }
    }
}