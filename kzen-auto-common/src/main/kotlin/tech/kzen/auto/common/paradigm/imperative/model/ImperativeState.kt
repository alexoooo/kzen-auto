package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.util.Digest


data class ImperativeState(
        val running: Boolean,
        val previous: ImperativeResult?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val initial = ImperativeState(false, null)


        fun toCollection(result: ImperativeState): Map<String, Any?> {
            return mapOf(
                    "running" to result.running,
                    "previous" to result.previous?.toCollection()
            )
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any?>): ImperativeState {
            return ImperativeState(
                    collection["running"] as Boolean,
                    collection["previous"]?.let {
                        ImperativeResult.fromCollection(it as Map<String, Any>)
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
        val digest = Digest.Streaming()

        digest.addBoolean(running)

        digest.addDigest(previous?.digest())

        return digest.digest()
    }
}