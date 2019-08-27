package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NumberExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess


@Suppress("unused")
class NumberLiteral(
        private val value: Double
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        return ImperativeSuccess(
                NumberExecutionValue(value),
                NullExecutionValue)
    }
}