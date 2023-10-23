package tech.kzen.auto.client.objects.document.report.preview.model

import tech.kzen.auto.common.objects.document.report.summary.TableSummary


data class ReportPreviewState(
    val tableSummary: TableSummary? = null,
    val previewError: String? = null
)