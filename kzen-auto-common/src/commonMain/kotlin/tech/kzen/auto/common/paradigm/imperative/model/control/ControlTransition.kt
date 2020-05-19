package tech.kzen.auto.common.paradigm.imperative.model.control

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.lib.common.model.document.DocumentPath


sealed class ControlTransition {
    companion object {
        private const val typeKey = "type"
        private const val branchKey = "branch"
        private const val valueKey = "value"
        private const val targetKey = "target"

        private const val evaluateType = "eval"
        private const val internalType = "internal"
        private const val invokeType = "invoke"


        fun fromCollection(collection: Map<String, Any?>): ControlTransition {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val type = collection[typeKey] as String

            return when (type) {
                evaluateType -> {
                    val value = readValue(collection)
                    EvaluateControlTransition(value)
                }

                internalType -> {
                    val branchIndex = collection[branchKey] as Int
                    val value = readValue(collection)
                    InternalControlTransition(branchIndex, value)
                }

                invokeType -> {
                    val target = DocumentPath.parse(collection[targetKey] as String)
                    InvokeControlTransition(target)
                }

                else ->
                    throw IllegalArgumentException("Unknown type: $collection")
            }
        }

        private fun readValue(collection: Map<String, Any?>): ExecutionValue {
            @Suppress("UNCHECKED_CAST")
            val valueMap = collection[valueKey] as Map<String, Any>
            return ExecutionValue.fromJsonCollection(valueMap)
        }
    }


    fun toCollection(): Map<String, Any?> {
        return when (this) {
            is EvaluateControlTransition ->
                mapOf(typeKey to evaluateType,
                        valueKey to value.toJsonCollection())

            is InternalControlTransition ->
                mapOf(typeKey to internalType,
                        branchKey to branchIndex,
                        valueKey to value.toJsonCollection())

            is InvokeControlTransition ->
                mapOf(typeKey to invokeType,
                        targetKey to target.asString())
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


data class InvokeControlTransition(
//        val target: ObjectLocation
        val target: DocumentPath
): ControlTransition()