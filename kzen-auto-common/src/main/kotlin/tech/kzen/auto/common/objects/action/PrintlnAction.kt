package tech.kzen.auto.common.objects.action

import tech.kzen.auto.common.paradigm.imperative.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionSuccess


@Suppress("unused")
class PrintlnAction(
        private val message: String
): ExecutionAction {
    override suspend fun perform(): ExecutionResult {
        println("PrintlnAction: $message")
        return ExecutionSuccess.empty
    }
}