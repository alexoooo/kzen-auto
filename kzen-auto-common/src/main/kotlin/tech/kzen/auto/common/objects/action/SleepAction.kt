package tech.kzen.auto.common.objects

import kotlinx.coroutines.delay
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.ExecutionSuccess


@Suppress("unused")
class SleepAction(
        private val seconds: Double
): AutoAction {
    override suspend fun perform(): ExecutionResult {
        delay((seconds * 1000).toLong())
        return ExecutionSuccess.empty
    }
}