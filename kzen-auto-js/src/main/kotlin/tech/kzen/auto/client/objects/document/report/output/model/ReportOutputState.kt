package tech.kzen.auto.client.objects.document.report.output.model

import tech.kzen.auto.common.objects.document.report.output.OutputInfo


data class ReportOutputState(
    val outputInfo: OutputInfo? = null,
    val outputInfoError: String? = null
)