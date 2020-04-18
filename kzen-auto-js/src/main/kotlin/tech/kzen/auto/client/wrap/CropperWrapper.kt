package tech.kzen.auto.client.wrap

import kotlinx.css.*
import org.w3c.dom.CustomEvent
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import styled.styledImg
import kotlin.js.json


class CropperWrapper:
        RPureComponent<CropperWrapper.Props, RState>()
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var src: String?,
            var crop: (event: CustomEvent) -> Unit
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    private var imageElement: HTMLImageElement? = null
    private var cropper: Cropper? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        console.log("cropperjs", cropperjs)

        val options = json()

//        options["aspectRatio"] = Double.NaN
        options["autoCropArea"] = 0.05
        options["crop"] = props.crop

        cropper = Cropper(imageElement!!, options)
    }


    override fun componentWillUnmount() {
        cropper?.destroy()
        cropper = null
        imageElement = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun getCroppedCanvas(): HTMLCanvasElement {
        val options = json()

        // https://github.com/fengyuanchen/cropperjs/blob/master/README.md#getcroppedcanvasoptions
        options["imageSmoothingEnabled"] = false
        options["maxWidth"] = 4096
        options["maxHeight"] = 4096

        return cropper!!.getCroppedCanvas(options)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                width = 100.pct
                height = 100.pct
            }

            styledImg {
                css {
                    opacity = 0
                    maxWidth = 100.pct
                    maxHeight = 100.pct
                }

                attrs {
                    src = props.src ?: "Screenshot"
                }

                ref {
                    imageElement = it as? HTMLImageElement
                }
            }
        }
    }
}