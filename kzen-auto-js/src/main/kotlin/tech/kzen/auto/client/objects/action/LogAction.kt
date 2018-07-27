package tech.kzen.auto.client.objects.action

import tech.kzen.auto.common.api.AutoAction


class LogAction(
        val message: String
): AutoAction {
    override fun perform() {
        println("Log: $message")
    }
}