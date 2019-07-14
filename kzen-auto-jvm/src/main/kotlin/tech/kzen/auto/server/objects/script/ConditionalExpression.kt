package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.lib.common.model.locate.ObjectLocation


class ConditionalExpression(
        private val condition: ObjectLocation
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
//        println("^^^^ branches - $branches")

        TODO()
    }
}