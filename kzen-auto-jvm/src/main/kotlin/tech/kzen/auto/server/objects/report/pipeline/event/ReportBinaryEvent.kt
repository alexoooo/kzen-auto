package tech.kzen.auto.server.objects.report.pipeline.event

import com.lmax.disruptor.EventFactory
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer


data class ReportBinaryEvent(
    var noop: Boolean = false,
    val data: RecordDataBuffer = RecordDataBuffer.ofBufferSize()
) {
    companion object {
        val factory = EventFactory { ReportBinaryEvent() }
    }
}