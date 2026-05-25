@file:JsModule("cropperjs")
package tech.kzen.auto.client.wrap.cropper

import web.html.HTMLElement
import web.html.HTMLImageElement
import kotlin.js.Json


@JsName("default")
external class Cropper(
        imageElement: HTMLImageElement,
        options: Json = definedExternally
) {
    fun getCropperCanvas(): HTMLElement?

    fun getCropperImage(): HTMLElement?

    // https://github.com/fengyuanchen/cropperjs/blob/main/packages/cropperjs/README.md#getcropperselection
    fun getCropperSelection(): CropperSelection?

    fun destroy()
}


external class CropperSelection: HTMLElement {
    var initialCoverage: Double
}


external interface CropperDetail {
    var x: Double
    var y: Double
    var width: Double
    var height: Double
}
