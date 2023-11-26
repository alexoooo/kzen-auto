package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue


data class StepValue<T>(
    val value: T?,
    val detail: ExecutionValue
) {
    companion object {
        fun <T> ofValue(value: T): StepValue<T> {
            return StepValue(value, NullExecutionValue)
        }
    }
}