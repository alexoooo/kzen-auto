package tech.kzen.auto.server.objects.script.logic

import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DivisibleCheck(
        private val number: ObjectLocation,
        private val divisor: Double
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
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