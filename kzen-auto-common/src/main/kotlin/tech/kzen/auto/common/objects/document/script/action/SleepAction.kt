package tech.kzen.auto.common.objects.document.script.action

import kotlinx.coroutines.delay
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess


@Suppress("unused")
class SleepAction(
        private val seconds: Double
): ExecutionAction {
    override suspend fun perform(): ImperativeResult {
        delay((seconds * 1000).toLong())
        return ImperativeSuccess.empty
    }
}