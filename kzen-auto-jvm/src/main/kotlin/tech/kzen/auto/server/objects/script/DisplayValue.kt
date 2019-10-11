package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class DisplayValue(
        private val text: ObjectLocation
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        val frame = imperativeModel.findLast(text)
        val state = frame?.states?.get(text.objectPath)
        val result = state?.previous as? ExecutionSuccess
        val value = result?.value ?: NullExecutionValue

        return ExecutionSuccess(
                NullExecutionValue,
                value)
    }
}