package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.instance.GraphInstance


@Suppress("unused")
class NumberRange(
        private val from: Int,
        private val to: Int
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val items = mutableListOf<NumberExecutionValue>()
        for (n in from .. to) {
            items.add(NumberExecutionValue(n.toDouble()))
        }
        return ExecutionSuccess(
                ListExecutionValue(items),
                NullExecutionValue)
    }
}