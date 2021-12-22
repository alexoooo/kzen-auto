package tech.kzen.auto.client.objects.document.common.run

import tech.kzen.auto.common.paradigm.common.v1.model.LogicStatus


data class ExecutionRunState(
    val logicStatus: LogicStatus? = null,
    val otherRunning: Boolean = false,

//    val progress: LogicTraceSnapshot? = null,

    val starting: Boolean = false,
    val cancelling: Boolean = false,

    val runError: String? = null
) {
    fun submitting(): Boolean {
        return starting || cancelling
    }
}
