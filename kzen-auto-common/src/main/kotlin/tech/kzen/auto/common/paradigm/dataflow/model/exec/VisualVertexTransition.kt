package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.lib.common.model.locate.ObjectLocation


data class VisualVertexTransition(
        val stateChange: ExecutionValue?,
        val message: ExecutionValue?,
        val hasNext: Boolean,
        val iteration: Int,
        val cleared: List<ObjectLocation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val stateChangeKey = "stateChange"
        private val messageKey = "message"
        private val hasNextKey = "hasNext"
        private val iterationKey = "iteration"
        private val clearedKey = "cleared"


        fun toCollection(model: VisualVertexTransition): Map<String, Any?> {
            return mapOf(
                    stateChangeKey to model.stateChange?.toCollection(),
                    messageKey to model.message?.toCollection(),
                    hasNextKey to model.hasNext,
                    iterationKey to model.iteration,
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
                    collection[iterationKey] as Int,
                    (collection[clearedKey] as List<String>).map { ObjectLocation.parse(it) }
            )
        }
    }
}