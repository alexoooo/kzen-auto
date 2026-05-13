package tech.kzen.auto.client.wrap.cropper

import emotion.react.css
import react.ChildrenBuilder
import react.PropsWithRef
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import web.cssom.Position
import web.cssom.number
import web.cssom.pct
import web.events.CustomEvent
import web.html.HTMLCanvasElement
import web.html.HTMLImageElement
import kotlin.js.Promise
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
        cropper = Cropper(imageElement.current!!)

        // <cropper-canvas> defaults to min-height:100px; stretch it to fill the wrapper div.
        // Note: `let` on `asDynamic()` becomes a JS member call at runtime (no `.let` on raw JS objects),
        // so assign style fields directly on the dynamic receiver instead.
        val canvas = cropper?.getCropperCanvas()
        if (canvas != null) {
            val style = canvas.asDynamic().style
            style.width = "100%"
            style.height = "80%"
        }

        val selection = cropper?.getCropperSelection()
            ?: return

        selection.initialCoverage = 0.05

        // CropperSelection dispatches a `change` CustomEvent with detail = CropperDetail.
        // kotlin-wrappers' typed addEventListener requires HasTargets which CustomEvent doesn't implement,
        // so register the raw listener via dynamic to keep the existing CustomEvent<CropperDetail> contract.
        selection.asDynamic().addEventListener("change") { event: dynamic ->
            @OptIn(ExperimentalWasmJsInterop::class)
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            props.crop(event.unsafeCast<CustomEvent<CropperDetail>>())
        }
    }


    override fun componentWillUnmount() {
        cropper?.destroy()
        cropper = null
//        imageElement.current = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun getCroppedCanvas(): Promise<HTMLCanvasElement> {
        val options = json()

        // https://github.com/fengyuanchen/cropperjs/blob/main/packages/element-selection/README.md#tocanvasoptions
        options["imageSmoothingEnabled"] = false
        options["maxWidth"] = 4096
        options["maxHeight"] = 4096

        return cropper!!.getCropperSelection()!!.toCanvas(options)
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