package tech.kzen.auto.common.objects

import tech.kzen.auto.common.api.AutoAction


class PrintlnAction(
        val message: String
): AutoAction {
    override fun perform() {
        println("PrintlnAction: $message")
    }
}