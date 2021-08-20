package tech.kzen.auto.client.objects.document.pipeline.run.model

import tech.kzen.auto.common.paradigm.common.v1.model.LogicStatus


data class PipelineRunState(
    val logicStatus: LogicStatus? = null,
    val progress: PipelineRunProgress? = null,

    val starting: Boolean = false,
    val cancelling: Boolean = false,

    val runError: String? = null
) {
    fun submitting(): Boolean {
        return starting || cancelling
    }
}
