package tech.kzen.auto.common.objects.document.script.action

import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation


class ReferenceAction(
        private val value: ObjectLocation
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val frame = imperativeModel.findLast(value)
        val state = frame?.states?.get(value.objectPath)
        val result = state?.previous as? ImperativeSuccess
        val reference = result?.value ?: NullExecutionValue

        return ImperativeSuccess(
                reference,
                NullExecutionValue)
    }
}