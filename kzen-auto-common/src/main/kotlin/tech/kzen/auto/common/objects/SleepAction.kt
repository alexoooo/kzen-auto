package tech.kzen.auto.common.objects

import kotlinx.coroutines.delay
import tech.kzen.auto.common.api.AutoAction


@Suppress("unused")
class SleepAction(
        private val seconds: Double
): AutoAction {
    override suspend fun perform() {
        delay((seconds * 1000).toLong())
    }
}