package tech.kzen.auto.server.objects.script.logic

import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class LogicalAnd(
        private val condition: ObjectLocation,
        private val and: ObjectLocation
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val conditionResult = get(condition, imperativeModel)
        val andResult = get(and, imperativeModel)

        val value = apply(
                conditionResult, andResult)

        return ExecutionSuccess(
                value,
                NullExecutionValue)
    }


    private fun apply(
            conditionResult: BooleanExecutionValue?,
            andResult: BooleanExecutionValue?
    ): ExecutionValue {
        if (conditionResult == null || andResult == null) {
            return NullExecutionValue
        }

        val value = conditionResult.value && andResult.value

        return BooleanExecutionValue(value)
    }


    private fun get(
            objectLocation: ObjectLocation,
            imperativeModel: ImperativeModel
    ): BooleanExecutionValue? {
        val frame = imperativeModel.findLast(objectLocation)
        val state = frame?.states?.get(objectLocation.objectPath)
        val result = state?.previous as? ExecutionSuccess
        return result?.value as? BooleanExecutionValue
    }
}