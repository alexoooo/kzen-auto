package tech.kzen.auto.client.objects.document.pipeline.preview.model

import tech.kzen.auto.common.objects.document.report.summary.TableSummary


data class PipelinePreviewState(
    val tableSummary: TableSummary? = null,
    val previewError: String? = null
)