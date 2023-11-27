package tech.kzen.auto.client.objects.document.report.run.model

import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTraceSnapshot


data class ReportRunProgress(
//    val message: String,
    val snapshot: LogicTraceSnapshot
)
