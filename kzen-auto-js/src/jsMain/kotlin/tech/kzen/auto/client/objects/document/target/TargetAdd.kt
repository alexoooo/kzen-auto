package tech.kzen.auto.client.objects.document.target

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.cropper.CropperDetail
import tech.kzen.auto.client.wrap.cropper.CropperWrapper
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.lib.platform.IoUtils
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface TargetAddProps: Props {
    var screenshotDataUrl: String
    var onSave: (ByteArray) -> Unit
}


external interface TargetAddState: State {
    var detail: CropperDetail?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Capture a new patch: crop selector over the current screenshot, Save adds the cut as a
 * document resource (via the parent's onSave).
 */
@Suppress("unused")
class TargetAdd(
    props: TargetAddProps
):
    RPureComponent<TargetAddProps, TargetAddState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private var cropperWrapper: RefObject<CropperWrapper> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    override fun TargetAddState.init(props: TargetAddProps) {
        detail = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCrop(detail: CropperDetail) {
        setState {
            this.detail = detail
        }
    }


    private fun onSave() {
        state.detail
            ?: return

        cropperWrapper.current!!.getCroppedCanvas().then { canvas ->
            val dataUrl = canvas.toDataURL("image/png")
            val cropPng = IoUtils.base64Decode(dataUrl.substringAfter(","))

            props.onSave(cropPng)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                padding = Padding(0.px, 1.em, 0.px, 1.em)
            }

            div {
                css {
                    marginBottom = 0.5.em
                }
                renderSave()
            }

            renderCropper()
        }
    }


    private fun ChildrenBuilder.renderSave() {
        Button {
            sx {
                backgroundColor = NamedColor.white
            }

            variant = ButtonVariant.outlined
            size = Size.small

            onClick = { onSave() }

            icon("material-symbols:photo-camera") {
                style = unsafeJso {
                    marginRight = 0.25.em
                }
            }
            +"Save"
        }
    }


    private fun ChildrenBuilder.renderCropper() {
        div {
            css {
                width = 100.pct
                height = 100.vh.minus(14.em)
                minHeight = 200.px
                maxHeight = 1024.px
            }

            // The capture surface displays a screenshot that can itself contain the target's
            // pixels — never a match when a script automates the kzen-auto UI itself
            // (see TargetLocator)
            asDynamic()[TargetDocument.previewDataAttribute] = ""

            CropperWrapper::class.react {
                src = props.screenshotDataUrl

                crop = { event ->
                    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
                    @OptIn(ExperimentalWasmJsInterop::class)
                    val detail = event.detail as CropperDetail
                    onCrop(detail)
                }

                ref = cropperWrapper
            }
        }
    }
}
