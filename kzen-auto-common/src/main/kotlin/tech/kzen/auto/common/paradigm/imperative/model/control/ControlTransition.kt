package tech.kzen.auto.common.paradigm.imperative.model.control

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


sealed class ControlTransition {
    companion object {
        private const val branchKey = "branch"
        private const val valueKey = "value"


        fun fromCollection(collection: Map<String, Any?>): ControlTransition {
            val index = collection[branchKey] as? Int

            @Suppress("UNCHECKED_CAST")
            val value = collection[valueKey] as? Map<String, Any>

            return if (index == null) {
                EvaluateControlTransition
            }
            else {
                InternalControlTransition(index, ExecutionValue.fromCollection(value!!))
            }
        }
    }


    fun toCollection(): Map<String, Any?> {
        return when (this) {
            EvaluateControlTransition ->
                mapOf()

            is InternalControlTransition ->
                mapOf(branchKey to branchIndex)
        }
    }
}


object EvaluateControlTransition: ControlTransition()


data class InternalControlTransition(
        val branchIndex: Int,
        val value: ExecutionValue
): ControlTransition()