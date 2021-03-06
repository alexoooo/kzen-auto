package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlState
import tech.kzen.auto.common.paradigm.imperative.model.control.InitialControlState
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ImperativeState(
        val previous: ExecutionResult?,

        val controlState: ControlState?
):
        Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val previousKey = "previous"
        private const val controlStateKey = "controlState"

        val initialSingular = ImperativeState(null, null)
        val initialControlFlow = ImperativeState(null, InitialControlState)


        fun toCollection(state: ImperativeState): Map<String, Any?> {
            return mapOf(
                    previousKey to state.previous?.toJsonCollection(),
                    controlStateKey to state.controlState?.toCollection()
            )
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any?>): ImperativeState {
            return ImperativeState(
                    collection[previousKey]?.let {
                        ExecutionResult.fromJsonCollection(it as Map<String, Any>)
                    },
                    collection[controlStateKey]?.let {
                        ControlState.fromCollection(it as Map<String, Any>)
                    }
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun phase(running: Boolean): ImperativePhase {
        if (running) {
            return ImperativePhase.Running
        }

        if (previous == null) {
            return ImperativePhase.Pending
        }

        return when (previous) {
            is ExecutionFailure ->
                ImperativePhase.Error

            is ExecutionSuccess ->
                ImperativePhase.Success
        }
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleNullable(previous)
        builder.addDigestibleNullable(controlState)
    }
}