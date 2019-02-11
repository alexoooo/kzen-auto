package tech.kzen.auto.common.objects

import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.ExecutionSuccess


@Suppress("unused")
class PrintlnAction(
        private val message: String
): AutoAction {
    override suspend fun perform(): ExecutionResult {
        println("PrintlnAction: $message")
        return ExecutionSuccess.empty
    }
}