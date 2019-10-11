package tech.kzen.auto.common.objects.document.script.action

import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation


class ReferenceAction(
        private val value: ObjectLocation
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
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