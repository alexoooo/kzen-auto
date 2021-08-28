package tech.kzen.auto.client.objects.document.pipeline.output.model

import tech.kzen.auto.common.objects.document.report.output.OutputInfo


data class PipelineOutputState(
    val outputInfo: OutputInfo? = null,
    val outputInfoError: String? = null
)