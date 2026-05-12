@file:JsModule("cropperjs")
package tech.kzen.auto.client.wrap.cropper

import web.html.HTMLCanvasElement
import web.html.HTMLElement
import web.html.HTMLImageElement
import kotlin.js.Json
import kotlin.js.Promise


@JsName("default")
external class Cropper(
        imageElement: HTMLImageElement,
        options: Json = definedExternally
) {
    fun getCropperCanvas(): HTMLElement?

    // https://github.com/fengyuanchen/cropperjs/blob/main/packages/cropperjs/README.md#getcropperselection
    fun getCropperSelection(): CropperSelection?

    fun destroy()
}


external class CropperSelection: HTMLElement {
    var initialCoverage: Double

    @JsName("\$toCanvas")
    fun toCanvas(options: Json = definedExternally): Promise<HTMLCanvasElement>
}


external interface CropperDetail {
    var x: Double
    var y: Double
    var width: Double
    var height: Double
}
