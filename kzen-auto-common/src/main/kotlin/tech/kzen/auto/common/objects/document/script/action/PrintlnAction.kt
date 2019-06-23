package tech.kzen.auto.common.objects.document.script.action

import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess


@Suppress("unused")
class PrintlnAction(
        private val message: String
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        println("PrintlnAction: $message")
        return ImperativeSuccess.empty
    }
}