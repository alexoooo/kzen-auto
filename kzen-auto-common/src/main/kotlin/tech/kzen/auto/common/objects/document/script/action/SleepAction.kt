package tech.kzen.auto.common.objects.document.script.action

import kotlinx.coroutines.delay
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionSuccess


@Suppress("unused")
class SleepAction(
        private val seconds: Double
): ExecutionAction {
    override suspend fun perform(): ExecutionResult {
        delay((seconds * 1000).toLong())
        return ExecutionSuccess.empty
    }
}