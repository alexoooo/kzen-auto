package tech.kzen.auto.common.paradigm.imperative.model.control
//
//import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
//import tech.kzen.lib.common.model.document.DocumentPath
//import tech.kzen.lib.common.util.Digest
//import tech.kzen.lib.common.util.Digestible
//
//
////---------------------------------------------------------------------------------------------------------------------
//sealed class ControlState
//    : Digestible
//{
//    companion object {
//        private const val branchKey = "branch"
//        private const val typeKey = "type"
//        private const val valueKey = "value"
//        private const val targetKey = "target"
//
//        private const val initialType = "initial"
//        private const val finalType = "final"
//        private const val internalType = "internal"
//        private const val invokeType = "invoke"
//
//
//        @Suppress("UNCHECKED_CAST")
//        fun fromCollection(collection: Map<String, Any>): ControlState {
//            @Suppress("MoveVariableDeclarationIntoWhen")
//            val type = collection[typeKey] as String
//            val index = collection[branchKey] as? Int
//            val value = collection[valueKey] as? Map<String, Any>
//            val target = collection[targetKey] as? String
//
//            return when (type) {
//                initialType ->
//                    InitialControlState
//
//                finalType ->
//                    FinalControlState(ExecutionValue.fromJsonCollection(value!!))
//
//                internalType ->
//                    InternalControlState(index!!, ExecutionValue.fromJsonCollection(value!!))
//
//                invokeType ->
//                    InvokeControlState(DocumentPath.parse(target!!))
//
//                else ->
//                    throw IllegalArgumentException("Unknown: $collection")
//            }
//        }
//    }
//
//
//    fun toCollection(): Map<String, Any> {
//        return when (this) {
//            is InitialControlState ->
//                mapOf(typeKey to initialType)
//
//            is FinalControlState ->
//                mapOf(typeKey to finalType,
//                        valueKey to value.toJsonCollection())
//
//            is InternalControlState ->
//                mapOf(typeKey to internalType,
//                        branchKey to branchIndex,
//                        valueKey to value.toJsonCollection())
//
//            is InvokeControlState ->
//                mapOf(typeKey to invokeType,
//                        targetKey to target.asString())
//        }
//    }
//
//
//    override fun digest(): Digest {
//        val digest = Digest.Builder()
//        digest(digest)
//        return digest.digest()
//    }
//
//
//    override fun digest(sink: Digest.Sink) {
//        when (this) {
//            is InitialControlState -> {
//                sink.addInt(0)
//            }
//
//            is FinalControlState -> {
//                sink.addInt(1)
//                value.digest(sink)
//            }
//
//            is InternalControlState -> {
//                sink.addInt(2)
//                sink.addInt(branchIndex)
//                value.digest(sink)
//            }
//
//            is InvokeControlState -> {
//                sink.addInt(3)
//            }
//        }
//    }
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//object InitialControlState: ControlState()
//
//
//data class FinalControlState(
//        val value: ExecutionValue
//): ControlState()
//
//
////---------------------------------------------------------------------------------------------------------------------
//data class InternalControlState(
//        val branchIndex: Int,
//        val value: ExecutionValue
//): ControlState()
//
//
////---------------------------------------------------------------------------------------------------------------------
//data class InvokeControlState(
//        val target: DocumentPath
//): ControlState()
//
