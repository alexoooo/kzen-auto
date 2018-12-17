package tech.kzen.auto.common.objects

import tech.kzen.auto.common.api.AutoAction


@Suppress("unused")
class PrintlnAction(
        private val message: String
): AutoAction {
    override suspend fun perform() {
        println("PrintlnAction: $message")
    }
}