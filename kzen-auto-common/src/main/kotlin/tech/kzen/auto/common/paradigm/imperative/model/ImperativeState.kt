package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.auto.common.paradigm.imperative.model.control.ControlState
import tech.kzen.auto.common.paradigm.imperative.model.control.InitialControlState
import tech.kzen.lib.common.util.Digest


data class ImperativeState(
        val running: Boolean,
        val previous: ImperativeResult?,
        val controlState: ControlState?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val runningKey = "running"
        private const val previousKey = "previous"
        private const val controlStateKey = "controlState"

        val initialSingular = ImperativeState(false, null, null)
        val initialControlFlow = ImperativeState(false, null, InitialControlState)


        fun toCollection(state: ImperativeState): Map<String, Any?> {
            return mapOf(
                    runningKey to state.running,
                    previousKey to state.previous?.toCollection(),
                    controlStateKey to state.controlState?.toCollection()
            )
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any?>): ImperativeState {
            return ImperativeState(
                    collection[runningKey] as Boolean,
                    collection[previousKey]?.let {
                        ImperativeResult.fromCollection(it as Map<String, Any>)
                    },
                    collection[controlStateKey]?.let {
                        ControlState.fromCollection(it as Map<String, Any>)
                    }
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun phase(): ImperativePhase {
        if (running) {
            return ImperativePhase.Running
        }

        if (previous == null) {
            return ImperativePhase.Pending
        }

        return when (previous) {
            is ImperativeError ->
                ImperativePhase.Error

            is ImperativeSuccess ->
                ImperativePhase.Success
        }
    }


    fun digest(): Digest {
        val digest = Digest.Builder()

        digest.addBoolean(running)

        digest.addDigest(previous?.digest())

        digest.addDigest(controlState?.digest())

        return digest.digest()
    }
}