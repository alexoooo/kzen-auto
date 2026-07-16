package tech.kzen.auto.client.objects.document.script.display.image

import kotlinx.browser.window
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.platform.encodeURIComponent
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BinaryHandleExecutionValue
import tech.kzen.lib.common.exec.BinaryValue
import tech.kzen.lib.platform.IoUtils


// The single render choke point for a screenshot trace value. A live/inline binary renders as a base64 data
// URL (cached on the value, shared across the thumbnail, its floating preview, and the full-screen view). A
// content-addressed handle (the trace-wire representation — no bytes) renders as the immutable blob URL, which
// the browser fetches once and caches; TP3 removed the inline base64 from trace JSON in favour of this.
internal fun pngUrl(screenshot: BinaryValue): String =
    when (screenshot) {
        is BinaryExecutionValue ->
            screenshot.cache("img") {
                val base64 = IoUtils.base64Encode(screenshot.value)
                "data:image/png;base64,$base64"
            }

        is BinaryHandleExecutionValue ->
            traceBinaryUrl(screenshot.run, screenshot.hash)
    }


// The immutable blob URL of a handle-referenced screenshot. Built off the same relative prefix every REST call
// uses (window.location.pathname, exactly as ClientContext.baseUrl / ClientRestApi derive it), so it inherits
// the kzen-shell proxy prefix. Self-contained by design: pngUrl is a leaf render helper reached from many call
// sites with no ClientContext / ClientRestApi instance in scope. The one consumer that needs the actual bytes
// (TargetController's locate) fetches them via ClientRestApi.logicTraceBinaryBytes instead.
private fun traceBinaryUrl(runId: String, hash: String): String {
    val baseUrl = window.location.pathname.substringBeforeLast("/")
    return baseUrl + CommonRestApi.logicTraceBinary +
        "?" + CommonRestApi.paramRunId + "=" + encodeURIComponent(runId) +
        "&" + CommonRestApi.paramContentHash + "=" + encodeURIComponent(hash)
}
