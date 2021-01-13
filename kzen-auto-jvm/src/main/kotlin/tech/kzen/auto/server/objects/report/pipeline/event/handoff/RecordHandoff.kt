package tech.kzen.auto.server.objects.report.pipeline.event.handoff

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordMapBuffer


interface RecordHandoff {
    fun next(): RecordMapBuffer
    fun commit()
}