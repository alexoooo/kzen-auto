package tech.kzen.auto.client.objects.document.sequence.progress

import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId


data class SequenceProgressState(
    val mostRecentTrace: MostRecentResult? = null
) {
    data class MostRecentResult(
        val logicRunExecutionId: LogicRunExecutionId?
    )
}