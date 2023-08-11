package tech.kzen.auto.common.objects.document.script.action

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class ReferenceAction(
        private val value: ObjectLocation
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val frame = imperativeModel.findLast(value)
        val state = frame?.states?.get(value.objectPath)
        val result = state?.previous as? ExecutionSuccess
        val reference = result?.value ?: NullExecutionValue

        return ExecutionSuccess(
                reference,
                NullExecutionValue)
    }
}