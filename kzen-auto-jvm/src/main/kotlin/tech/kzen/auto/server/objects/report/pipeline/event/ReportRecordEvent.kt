package tech.kzen.auto.server.objects.report.pipeline.event

import com.lmax.disruptor.EventFactory
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordMapBuffer


data class ReportRecordEvent(
    var filterAllow: Boolean = false,
    val record: RecordMapBuffer = RecordMapBuffer()
) {
    companion object {
        val factory = EventFactory { ReportRecordEvent() }
    }
}