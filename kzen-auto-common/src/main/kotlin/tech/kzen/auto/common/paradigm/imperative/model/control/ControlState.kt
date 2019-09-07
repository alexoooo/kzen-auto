package tech.kzen.auto.common.paradigm.imperative.model.control

import tech.kzen.lib.common.util.Digest


sealed class ControlState {
    companion object {
        private const val indexKey = "index"
        private const val typeKey = "type"
        private const val initialType  = "initial"
        private const val finalType  = "final"
//        private const val internalType  = "internal"
        private const val branchType  = "branch"


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any>): ControlState {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val type = collection[typeKey] as String
            val index = collection[indexKey] as? Int

            return when (type) {
                initialType ->
                    InitialControlState

                finalType ->
                    FinalControlState

//                internalType ->
//                    InternalEvaluationState(index)

                branchType ->
                    BranchEvaluationState(index!!)

                else ->
                    throw IllegalArgumentException("Unknown: $collection")
            }
        }
    }


    fun toCollection(): Map<String, Any> {
        return when (this) {
            is InitialControlState ->
                mapOf(typeKey to initialType)

            is FinalControlState ->
                mapOf(typeKey to finalType)

            is BranchEvaluationState ->
                return mapOf(
                        typeKey to branchType,
                        indexKey to index
                )

//            is IndexedEvaluationState -> {
//                val type =
//                        if (this is InternalEvaluationState) {
//                            internalType
//                        }
//                        else {
//                            branchType
//                        }
//
//                return mapOf(
//                        typeKey to type,
//                        indexKey to index
//                )
//            }
        }
    }


    abstract fun digest(): Digest
}


object InitialControlState : ControlState() {
    override fun digest(): Digest {
        return Digest.ofUtf8(InitialControlState::class.simpleName)
    }
}


object FinalControlState : ControlState() {
    override fun digest(): Digest {
        return Digest.ofUtf8(FinalControlState::class.simpleName)
    }
}


//sealed class IndexedEvaluationState(
//        val index: Int
//) : ControlState()


//class InternalEvaluationState(
//        index: Int
//) : IndexedEvaluationState(index)


class BranchEvaluationState(
//        index: Int
        val index: Int
//) : IndexedEvaluationState(index)
) : ControlState() {
    override fun digest(): Digest {
        val digest = Digest.Builder()

        digest.addUtf8(BranchEvaluationState::class.simpleName)

        digest.addInt(index)

        return digest.digest()
    }
}

