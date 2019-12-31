package tech.kzen.auto.common.paradigm.imperative.model.control

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


sealed class ControlTransition {
    companion object {
        private const val branchKey = "branch"
        private const val valueKey = "value"


        fun fromCollection(collection: Map<String, Any?>): ControlTransition {
            val branchIndex = collection[branchKey] as? Int

            @Suppress("UNCHECKED_CAST")
            val valueMap = collection[valueKey] as Map<String, Any>
            val value = ExecutionValue.fromCollection(valueMap)

            return if (branchIndex == null) {
                EvaluateControlTransition(value)
            }
            else {
                InternalControlTransition(branchIndex, value)
            }
        }
    }


    fun toCollection(): Map<String, Any?> {
        return when (this) {
            is EvaluateControlTransition ->
                mapOf(valueKey to value.toCollection())

            is InternalControlTransition ->
                mapOf(branchKey to branchIndex,
                        valueKey to value.toCollection())
        }
    }
}


data class EvaluateControlTransition(
        val value: ExecutionValue
): ControlTransition()


data class InternalControlTransition(
        val branchIndex: Int,
        val value: ExecutionValue
): ControlTransition()