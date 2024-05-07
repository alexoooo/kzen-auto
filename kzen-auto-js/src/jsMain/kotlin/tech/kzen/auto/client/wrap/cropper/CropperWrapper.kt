package tech.kzen.auto.client.wrap.cropper

import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.wrap.RPureComponent
import web.cssom.Position
import web.cssom.number
import web.cssom.pct
import web.events.CustomEvent
import web.html.HTMLCanvasElement
import web.html.HTMLImageElement
import kotlin.js.json


//-----------------------------------------------------------------------------------------------------------------
external interface CropperWrapperProps: PropsWithRef<CropperWrapper> {
    var src: String?
    var crop: (event: CustomEvent<*>) -> Unit
}


class CropperWrapper:
        RPureComponent<CropperWrapperProps, State>()
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
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                width = 100.pct
                height = 100.pct
            }

            img {
                css {
                    opacity = number(0.0)
                    maxWidth = 100.pct
                    maxHeight = 100.pct
                }

                src = props.src ?: "Screenshot"

                ref = imageElement
            }
        }
    }
}