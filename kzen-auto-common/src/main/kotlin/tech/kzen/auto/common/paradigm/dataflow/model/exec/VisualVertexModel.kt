package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.persistentMapOf


// TODO: add support for detail logging
data class VisualVertexModel(
        val running: Boolean,
        val state: ExecutionValue?,
        val message: ExecutionValue?,
//        val remainingBatch: PersistentList<ExecutionValue>,
//        val streamHasNext: Boolean
        val hasNext: Boolean
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = VisualDataflowModel(persistentMapOf())

        private val runningKey = "running"
        private val stateKey = "state"
        private val messageKey = "message"
        private val hasNextKey = "hasNext"


        fun toCollection(model: VisualVertexModel): Map<String, Any?> {
            return mapOf(
                    runningKey to model.running,
                    stateKey to model.state?.toCollection(),
                    messageKey to model.message?.toCollection(),
                    hasNextKey to model.hasNext
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
                    collection[hasNextKey] as Boolean
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun digest(): Digest {
        val digest = Digest.Streaming()

        digest.addBoolean(running)

        digest.addDigest(state?.digest())

        digest.addDigest(message?.digest())

        digest.addBoolean(hasNext)

        return digest.digest()
    }
}