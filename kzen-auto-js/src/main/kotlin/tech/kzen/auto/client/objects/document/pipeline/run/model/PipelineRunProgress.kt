package tech.kzen.auto.client.objects.document.pipeline.run.model

import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot


data class PipelineRunProgress(
//    val message: String,
    val snapshot: LogicTraceSnapshot
)
