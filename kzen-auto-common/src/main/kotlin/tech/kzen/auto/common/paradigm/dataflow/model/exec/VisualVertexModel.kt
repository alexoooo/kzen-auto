package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.lib.common.util.Digest


// TODO: add support for logging (possibly with log levels)
data class VisualVertexModel(
        /**
         * If true, then the below values, which refer to the "current" time, are potentially stale.
         */
        val running: Boolean,

        /**
         * Null means stateless
         */
        val state: ExecutionValue?,

        /**
         * Null either no output or wasn't processed yet
         */
        val message: ExecutionValue?,

        /**
         * Might be from stream or batch, visually there's no distinction
         */
        val hasNext: Boolean,

        /**
         * Number of times executed in current block context, reset to zero and end of block
         */
        val iteration: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = VisualVertexModel(false, null, null, false, 0)

        private const val runningKey = "running"
        private const val stateKey = "state"
        private const val messageKey = "message"
        private const val hasNextKey = "hasNext"
        private const val iterationKey = "iteration"


        fun toCollection(model: VisualVertexModel): Map<String, Any?> {
            return mapOf(
                    runningKey to model.running,
                    stateKey to model.state?.toCollection(),
                    messageKey to model.message?.toCollection(),
                    hasNextKey to model.hasNext,
                    iterationKey to model.iteration
            )
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(
                collection: Map<String, Any?>
        ): VisualVertexModel {
            return VisualVertexModel(
                    collection[runningKey] as Boolean,
                    collection[stateKey]?.let {
                        ExecutionValue.fromCollection(it as Map<String, Any>)
                    },
                    collection[messageKey]?.let {
                        ExecutionValue.fromCollection(it as Map<String, Any>)
                    },
                    collection[hasNextKey] as Boolean,
                    collection[iterationKey] as Int
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun phase(): VisualVertexPhase {
        // TODO: add support for Error
        return when {
            running ->
                VisualVertexPhase.Running

            iteration == 0 ->
                VisualVertexPhase.Pending

            hasNext ->
                VisualVertexPhase.Remaining

            else ->
                VisualVertexPhase.Done
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun digest(): Digest {
        val digest = Digest.Streaming()

        digest.addBoolean(running)

        digest.addDigest(state?.digest())

        digest.addDigest(message?.digest())

        digest.addBoolean(hasNext)

        digest.addInt(iteration)

        return digest.digest()
    }
}