package tech.kzen.auto.client.wrap.cropper

import web.html.HTMLCanvasElement
import web.html.HTMLImageElement
import kotlin.js.Json


@JsModule("cropperjs")
external class Cropper(
        imageElement: HTMLImageElement,
        options: Json
) {
    // https://github.com/fengyuanchen/cropperjs/blob/master/README.md#destroy
    fun destroy()

    // https://github.com/fengyuanchen/cropperjs/blob/master/README.md#getcroppedcanvasoptions
    fun getCroppedCanvas(optional: Json): HTMLCanvasElement
}


external interface CropperDetail {
    var x: Double
    var y: Double
    var width: Double
    var height: Double
}