package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation


// TODO: add error handling and logging
// TODO: stateChange and message are required, but can the rest be inferred on the client?
data class VisualVertexTransition(
    val stateChange: ExecutionValue?,
    val message: ExecutionValue?,
    val hasNext: Boolean,
    val iteration: Int,
    val loop: List<ObjectLocation>,
    val cleared: List<ObjectLocation>,
    val error: String?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val stateChangeKey = "stateChange"
        private const val messageKey = "message"
        private const val hasNextKey = "hasNext"
        private const val epochKey = "epoch"
        private const val loopKey = "loop"
        private const val clearedKey = "cleared"
        private const val errorKey = "error"


        fun toCollection(model: VisualVertexTransition): Map<String, Any?> {
            return mapOf(
                stateChangeKey to model.stateChange?.toJsonCollection(),
                messageKey to model.message?.toJsonCollection(),
                hasNextKey to model.hasNext,
                epochKey to model.iteration,
                loopKey to model.loop.map { it.asString() },
                clearedKey to model.cleared.map { it.asString() },
                errorKey to model.error
            )
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(
            collection: Map<String, Any?>
        ): VisualVertexTransition {
            return VisualVertexTransition(
                collection[stateChangeKey]?.let {
                    ExecutionValue.fromJsonCollection(it as Map<String, Any>)
                },
                collection[messageKey]?.let {
                    ExecutionValue.fromJsonCollection(it as Map<String, Any>)
                },
                collection[hasNextKey] as Boolean,
                collection[epochKey] as Int,
                (collection[loopKey] as List<String>).map { ObjectLocation.parse(it) },
                (collection[clearedKey] as List<String>).map { ObjectLocation.parse(it) },
                collection[errorKey] as? String
            )
        }
    }
}