package tech.kzen.auto.common.objects.document.script.control

import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.imperative.api.ScriptControl
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.control.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class ConditionalExpression(
        private val condition: ObjectLocation,
        private val then: List<ObjectLocation>,
        private val `else`: List<ObjectLocation>
): ScriptControl {
    override fun control(
            imperativeModel: ImperativeModel,
            controlState: ControlState
    ): ControlTransition {
        return when (controlState) {
            is InitialControlState -> {
                val conditionResult = result(condition, imperativeModel) as? ExecutionSuccess
                val conditionValue = (conditionResult?.value as? BooleanExecutionValue)?.value ?: false

                val branchIndex =
                        if (conditionValue) {
                            0
                        }
                        else {
                            1
                        }

                InternalControlTransition(branchIndex, NullExecutionValue)
            }

            is InternalControlState ->
                EvaluateControlTransition(
                        NullExecutionValue)

            else ->
                throw IllegalStateException()
        }
    }


    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val conditionResult = result(condition, imperativeModel) as? ExecutionSuccess
        val conditionValue = (conditionResult?.value as? BooleanExecutionValue)?.value ?: false

        return if (conditionValue) {
            if (then.isEmpty()) {
                ExecutionSuccess.empty
            }
            else {
                result(then.last(), imperativeModel)
                        ?: ExecutionFailure("'then' missing")

            }
        }
        else {
            if (`else`.isEmpty()) {
                ExecutionSuccess.empty
            }
            else {
                result(`else`.last(), imperativeModel)
                    ?: ExecutionFailure("'else' missing")
            }
        }
    }


    private fun result(
            dependency: ObjectLocation,
            imperativeModel: ImperativeModel
    ): ExecutionResult? {
        val conditionFrame = imperativeModel.findLast(dependency)
        val conditionState = conditionFrame?.states?.get(dependency.objectPath)
        return conditionState?.previous
    }
}