package tech.kzen.auto.client.objects.document.feature

import kotlinx.css.*
import react.*
import react.dom.br
import react.dom.img
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.DocumentArchetype


class FeatureController:
        RPureComponent<RProps, FeatureController.State>()
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var detail: CropperDetail?,
            var dataUrl: String?
    ): RState



    //-----------------------------------------------------------------------------------------------------------------
    private var cropperWrapper: CropperWrapper? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: RProps) {
        detail = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            private val type: DocumentArchetype
    ):
            DocumentController
    {
        override fun type(): DocumentArchetype {
            return type
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
            dataUrl = canvas.toDataURL()
        }
    }


    private fun onCapture() {
        setState {
            dataUrl = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                padding(1.em, 1.em, 0.px, 1.em)
            }

            val dataUrl = state.dataUrl
            if (dataUrl != null) {
                renderCapture()

                br {}

                renderDataUrl(dataUrl)
            }
            else {
                renderSave()

                br {}

                renderCropper()
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


    private fun RBuilder.renderCropper() {
        styledDiv {
            css {
                width = 100.pct
                height = 100.vh.minus(9.em)
                minHeight = 200.px
                maxHeight = 1024.px
            }

            child(CropperWrapper::class) {
                attrs {
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