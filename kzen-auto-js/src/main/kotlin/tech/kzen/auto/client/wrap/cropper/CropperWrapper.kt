package tech.kzen.auto.client.wrap.cropper

import kotlinx.css.*
import org.w3c.dom.CustomEvent
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import react.RBuilder
import react.RPureComponent
import react.RefObject
import react.createRef
import react.dom.attrs
import styled.css
import styled.styledDiv
import styled.styledImg
import kotlin.js.json


//-----------------------------------------------------------------------------------------------------------------
external interface CropperWrapperProps: react.Props {
    var src: String?
    var crop: (event: CustomEvent) -> Unit
}


class CropperWrapper:
        RPureComponent<CropperWrapperProps, react.State>()
{


    //-----------------------------------------------------------------------------------------------------------------
    private var imageElement: RefObject<HTMLImageElement> = createRef()
    private var cropper: Cropper? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        console.log("cropperjs", cropperjs)

        val options = json()

//        options["aspectRatio"] = Double.NaN
        options["autoCropArea"] = 0.05
        options["crop"] = props.crop

        cropper = Cropper(imageElement.current!!, options)
    }


    override fun componentWillUnmount() {
        cropper?.destroy()
        cropper = null
//        imageElement.current = null
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

                ref = imageElement
//                ref {
//                    imageElement = it as? HTMLImageElement
//                }
            }
        }
    }
}