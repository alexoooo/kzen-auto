package tech.kzen.auto.common.exec

import tech.kzen.lib.common.util.Digest


data class ExecutionState(
        val running: Boolean,
        val previous: Outcome?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val initial = ExecutionState(false, null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Outcome(
            val result: ExecutionResult,
            val resultDigest: Digest
    )


    //-----------------------------------------------------------------------------------------------------------------
    fun phase(): ExecutionPhase {
        if (running) {
            return ExecutionPhase.Running
        }

        if (previous == null) {
            return ExecutionPhase.Pending
        }

        return when (previous.result) {
            is ExecutionError ->
                ExecutionPhase.Error

            is ExecutionSuccess ->
                ExecutionPhase.Success
        }
    }


    fun digest(): Digest {
        val digest = Digest.Streaming()

        digest.addByte(if (running) 1 else 0)

        if (previous == null) {
            digest.addMissing()
        }
        else {
            digest.addDigest(previous.resultDigest)
        }

        return digest.digest()
    }
}