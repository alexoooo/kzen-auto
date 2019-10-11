package tech.kzen.auto.server.objects.script.logic

import tech.kzen.auto.common.paradigm.common.model.BooleanExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NumberExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class DivisibleCheck (
        private val number: ObjectLocation,
        private val divisor: Double
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        val frame = imperativeModel.findLast(number)
        val state = frame?.states?.get(number.objectPath)
        val result = state?.previous as? ExecutionSuccess
        val numberResult = result?.value as? NumberExecutionValue

        val divisible =
                numberResult?.value?.let {
                    BooleanExecutionValue(
                            it % divisor == 0.0)
                }
                ?: NullExecutionValue

        return ExecutionSuccess(
                divisible,
                NullExecutionValue)
    }
}