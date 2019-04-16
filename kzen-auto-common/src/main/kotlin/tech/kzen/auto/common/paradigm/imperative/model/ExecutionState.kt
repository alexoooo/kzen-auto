package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.util.Digest


data class ExecutionState(
        val running: Boolean,
        val previous: ExecutionResult?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val initial = ExecutionState(false, null)


        fun toCollection(result: ExecutionState): Map<String, Any?> {
            return mapOf(
                    "running" to result.running,
                    "previous" to result.previous?.toCollection()
            )
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any?>): ExecutionState {
            return ExecutionState(
                    collection["running"] as Boolean,
                    collection["previous"]?.let {
                        ExecutionResult.fromCollection(it as Map<String, Any>)
                    }
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun phase(): ExecutionPhase {
        if (running) {
            return ExecutionPhase.Running
        }

        if (previous == null) {
            return ExecutionPhase.Pending
        }

        return when (previous) {
            is ExecutionError ->
                ExecutionPhase.Error

            is ExecutionSuccess ->
                ExecutionPhase.Success
        }
    }


    fun digest(): Digest {
        val digest = Digest.Streaming()

        digest.addBoolean(running)

        if (previous == null) {
            digest.addMissing()
        }
        else {
            digest.addDigest(previous.digest())
        }

        return digest.digest()
    }
}