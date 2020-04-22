package tech.kzen.auto.common.objects.document.script.invoke

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ScriptControl
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.control.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class InvokeCall(
        private val selfLocation: ObjectLocation,
        private val script: ObjectLocation
): ScriptControl {
    override fun control(
            imperativeModel: ImperativeModel,
            controlState: ControlState
    ): ControlTransition {
        return when (controlState) {
            InitialControlState ->
                InvokeControlTransition(script.documentPath)

            is InvokeControlState ->
                EvaluateControlTransition(NullExecutionValue)

            else ->
                throw IllegalArgumentException("Unknown: $controlState")
        }
    }


    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val selfFrame = imperativeModel.findLast(selfLocation)!!
        val selfState = selfFrame.states[selfLocation.objectPath]!!
        val selfControl = selfState.controlState!! as FinalControlState

        return ExecutionSuccess(
                selfControl.value,
                NullExecutionValue)
    }
}