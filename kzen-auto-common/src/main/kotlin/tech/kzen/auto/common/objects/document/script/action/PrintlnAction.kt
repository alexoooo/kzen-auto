package tech.kzen.auto.common.objects.document.script.action

import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess


@Suppress("unused")
class PrintlnAction(
        private val message: String
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        println("PrintlnAction: $message")
        return ExecutionSuccess.empty
    }
}