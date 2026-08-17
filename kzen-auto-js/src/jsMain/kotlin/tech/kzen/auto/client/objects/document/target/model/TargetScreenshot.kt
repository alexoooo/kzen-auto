package tech.kzen.auto.client.objects.document.target.model

import tech.kzen.lib.platform.IoUtils


/**
 * The editor's working screenshot in both forms it is needed in: the PNG bytes that go to the server for
 * matching, and the data URL the overlay and the crop selector paint. Encoded once at acquisition — a
 * render-time encode would re-run over a full-screen image on every publish.
 */
class TargetScreenshot(
    val png: ByteArray
) {
    val dataUrl: String = "data:image/png;base64," + IoUtils.base64Encode(png)
}
