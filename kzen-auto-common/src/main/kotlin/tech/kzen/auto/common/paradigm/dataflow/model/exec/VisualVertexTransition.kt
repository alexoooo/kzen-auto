package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.lib.common.model.locate.ObjectLocation


// TODO: stateChange and message are required, but can the rest be inferred on the client?
data class VisualVertexTransition(
        val stateChange: ExecutionValue?,
        val message: ExecutionValue?,
        val hasNext: Boolean,
        val iteration: Int,
        val loop: List<ObjectLocation>,
        val cleared: List<ObjectLocation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val stateChangeKey = "stateChange"
        private const val messageKey = "message"
        private const val hasNextKey = "hasNext"
        private const val epochKey = "epoch"
        private const val loopKey = "loop"
        private const val clearedKey = "cleared"


        fun toCollection(model: VisualVertexTransition): Map<String, Any?> {
            return mapOf(
                    stateChangeKey to model.stateChange?.toCollection(),
                    messageKey to model.message?.toCollection(),
                    hasNextKey to model.hasNext,
                    epochKey to model.iteration,
                    loopKey to model.loop.map { it.asString() },
                    clearedKey to model.cleared.map { it.asString() }
            )
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(
                collection: Map<String, Any?>
        ): VisualVertexTransition {
            return VisualVertexTransition(
                    collection[stateChangeKey]?.let {
                        ExecutionValue.fromCollection(it as Map<String, Any>)
                    },
                    collection[messageKey]?.let {
                        ExecutionValue.fromCollection(it as Map<String, Any>)
                    },
                    collection[hasNextKey] as Boolean,
                    collection[epochKey] as Int,
                    (collection[loopKey] as List<String>).map { ObjectLocation.parse(it) },
                    (collection[clearedKey] as List<String>).map { ObjectLocation.parse(it) }
            )
        }
    }
}