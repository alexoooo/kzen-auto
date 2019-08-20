package tech.kzen.auto.common.paradigm.imperative.model.control


sealed class ControlTransition {
    companion object {
        private const val indexKey = "index"


        fun fromCollection(collection: Map<String, Any?>): ControlTransition {
            val index = collection[indexKey] as? Int

            return if (index == null) {
                EvaluateControlTransition
            }
            else {
                BranchExecutionTransition(index)
            }
        }
    }


    fun toCollection(): Map<String, Any?> {
        return when (this) {
            EvaluateControlTransition ->
                mapOf()

            is BranchExecutionTransition ->
                mapOf(indexKey to index)
        }
    }
}


object EvaluateControlTransition : ControlTransition()


//data class InternalExecutionTransition(
//        val index: Int
//) : ControlTransition()


data class BranchExecutionTransition(
        val index: Int
) : ControlTransition()