package tech.kzen.auto.server.objects.script.logic

import tech.kzen.auto.common.paradigm.common.model.BooleanExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class LogicalNot(
        private val negate: ObjectLocation
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val frame = imperativeModel.findLast(negate)
        val state = frame?.states?.get(negate.objectPath)
        val result = state?.previous as? ImperativeSuccess
        val booleanResult = result?.value as? BooleanExecutionValue

        val negation = booleanResult
                ?.let { BooleanExecutionValue(! it.value) }
                ?: NullExecutionValue

        return ImperativeSuccess(
                negation,
                NullExecutionValue)
    }
}