package tech.kzen.auto.client.wrap.cropper

import emotion.react.css
import kotlinx.browser.document
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
import kotlin.math.roundToInt


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
        // Bit-exact pixel copy from the source img — template matchers (Script click targets) require
        // that the saved bytes equal what a screenshot of the same region would contain.
        // cropperjs's $toCanvas composes fractional ctx.transform + drawImage with the default
        // imageSmoothingEnabled=true, so any non-integer alignment bilinearly filters the output.
        val img = imageElement.current!!
        val naturalW = img.naturalWidth.toDouble()
        val naturalH = img.naturalHeight.toDouble()

        // Cropper-image matrix [a, 0, 0, d, tx, ty] is a CSS transform with default origin 50% 50%,
        // which composes as translate(cx,cy) · matrix · translate(-cx,-cy). The net mapping
        // source pixel (px, py) → canvas (a·px + effectiveTx, d·py + effectiveTy) collapses
        // the origin into a single affine translation.
        val matrix = cropper!!.getCropperImage()!!.asDynamic().`$getTransform`()
        val a = (matrix[0] as Number).toDouble()
        val d = (matrix[3] as Number).toDouble()
        val tx = (matrix[4] as Number).toDouble()
        val ty = (matrix[5] as Number).toDouble()
        val effectiveTx = tx + (naturalW / 2.0) * (1.0 - a)
        val effectiveTy = ty + (naturalH / 2.0) * (1.0 - d)

        val sel = cropper!!.getCropperSelection()!!.asDynamic()
        val selX = (sel.x as Number).toDouble()
        val selY = (sel.y as Number).toDouble()
        val selW = (sel.width as Number).toDouble()
        val selH = (sel.height as Number).toDouble()

        val srcX = ((selX - effectiveTx) / a).roundToInt()
        val srcY = ((selY - effectiveTy) / d).roundToInt()
        val srcW = (selW / a).roundToInt()
        val srcH = (selH / d).roundToInt()

        val canvas = document.createElement("canvas").unsafeCast<HTMLCanvasElement>()
        canvas.width = srcW
        canvas.height = srcH

        val ctx = canvas.asDynamic().getContext("2d")
        ctx.imageSmoothingEnabled = false
        ctx.drawImage(img, srcX, srcY, srcW, srcH, 0, 0, srcW, srcH)

        return Promise.resolve(canvas)
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