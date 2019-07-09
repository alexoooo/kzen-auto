package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class DisplayValue(
        private val text: ObjectLocation
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val frame = imperativeModel.findLast(text)
        val state = frame?.states?.get(text.objectPath)
        val result = state?.previous as? ImperativeSuccess
        val value = result?.value ?: NullExecutionValue

        return ImperativeSuccess(
                NullExecutionValue,
                value)
    }
}