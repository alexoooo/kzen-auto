package tech.kzen.auto.server.objects

import tech.kzen.auto.common.api.AutoAction


class PrintlnAction(
        private val message: String
): AutoAction {
    override fun perform() {
        System.out.println(message)
    }
}