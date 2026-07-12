package tech.kzen.auto.client.objects.document.script.display.image

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.platform.IoUtils


// base64 data URL, cached on the screenshot value (shared across the thumbnail, its floating
// preview, and the full-screen view, including navigated-to neighbours).
internal fun pngUrl(screenshot: BinaryExecutionValue): String =
    screenshot.cache("img") {
        val base64 = IoUtils.base64Encode(screenshot.value)
        "data:image/png;base64,$base64"
    }