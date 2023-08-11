package tech.kzen.auto.server.objects.script.logic

import tech.kzen.auto.common.paradigm.common.model.BooleanExecutionValue
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class LogicalNot(
        private val negate: ObjectLocation
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val frame = imperativeModel.findLast(negate)
        val state = frame?.states?.get(negate.objectPath)
        val result = state?.previous as? ExecutionSuccess
        val booleanResult = result?.value as? BooleanExecutionValue

        val negation = booleanResult
                ?.let { BooleanExecutionValue(! it.value) }
                ?: NullExecutionValue

        return ExecutionSuccess(
                negation,
                NullExecutionValue)
    }
}