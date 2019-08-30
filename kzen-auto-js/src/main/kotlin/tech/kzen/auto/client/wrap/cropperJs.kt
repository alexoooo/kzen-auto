package tech.kzen.auto.client.wrap

import org.w3c.dom.HTMLImageElement
import kotlin.js.Json


@JsModule("cropperjs")
external class Cropper(
        imageElement: HTMLImageElement,
        options: Json
) {
    fun destroy()
}