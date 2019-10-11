package tech.kzen.auto.common.objects.document.script.action

import kotlinx.coroutines.delay
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess


@Suppress("unused")
class SleepAction(
        private val seconds: Double
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        delay((seconds * 1000).toLong())
        return ExecutionSuccess.empty
    }
}