package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


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
        val epoch: Int,

        /**
         * If present, means something went wrong
         */
        val error: String?
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = VisualVertexModel(
                false,
                null,
                null,
                false,
                0,
                null)

        private const val runningKey = "running"
        private const val stateKey = "state"
        private const val messageKey = "message"
        private const val hasNextKey = "hasNext"
        private const val epochKey = "epoch"
        private const val errorKey = "error"


        fun toCollection(model: VisualVertexModel): Map<String, Any?> {
            return mapOf(
                    runningKey to model.running,
                    stateKey to model.state?.toCollection(),
                    messageKey to model.message?.toCollection(),
                    hasNextKey to model.hasNext,
                    epochKey to model.epoch,
                    errorKey to model.error
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
                    collection[epochKey] as Int,
                    collection[errorKey] as? String
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun phase(): VisualVertexPhase {
        // TODO: add support for Error
        return when {
            running ->
                VisualVertexPhase.Running

            epoch == 0 ->
                VisualVertexPhase.Pending

            hasNext ->
                VisualVertexPhase.Remaining

            else ->
                VisualVertexPhase.Done
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addBoolean(running)

        builder.addDigestibleNullable(state)
        builder.addDigestibleNullable(message)
        builder.addBoolean(hasNext)
        builder.addInt(epoch)
    }
}