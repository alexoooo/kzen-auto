package tech.kzen.auto.server.objects.script.control

import tech.kzen.auto.common.paradigm.common.model.BooleanExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ControlFlow
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeError
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.auto.common.paradigm.imperative.model.control.*
import tech.kzen.lib.common.model.locate.ObjectLocation
import java.lang.IllegalStateException


@Suppress("unused")
class ConditionalExpression(
        private val condition: ObjectLocation,
        private val then: List<ObjectLocation>,
        private val `else`: List<ObjectLocation>
) : ControlFlow {
    override fun control(
            imperativeModel: ImperativeModel,
            controlState: ControlState
    ): ControlTransition {
        return when (controlState) {
            is InitialControlState -> {
                val conditionResult = result(condition, imperativeModel) as? ImperativeSuccess
                val conditionValue = (conditionResult?.value as? BooleanExecutionValue)?.value ?: false

                val branchIndex =
                        if (conditionValue) {
                            0
                        }
                        else {
                            1
                        }

                BranchExecutionTransition(branchIndex)
            }

            is BranchEvaluationState ->
                EvaluateControlTransition

            else ->
                throw IllegalStateException()
        }
    }


    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val conditionResult = result(condition, imperativeModel) as? ImperativeSuccess
        val conditionValue = (conditionResult?.value as? BooleanExecutionValue)?.value ?: false

        return if (conditionValue) {
            result(then.last(), imperativeModel)
                    ?: ImperativeError("'then' missing")
        }
        else {
            result(`else`.last(), imperativeModel)
                    ?: ImperativeError("'else' missing")
        }
    }


    private fun result(
            dependency: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ImperativeResult? {
        val conditionFrame = imperativeModel.findLast(dependency)
        val conditionState = conditionFrame?.states?.get(dependency.objectPath)
        return conditionState?.previous
    }
}