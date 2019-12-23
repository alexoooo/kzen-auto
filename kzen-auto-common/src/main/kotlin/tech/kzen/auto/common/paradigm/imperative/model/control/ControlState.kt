package tech.kzen.auto.common.paradigm.imperative.model.control

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.lib.common.util.Digest


sealed class ControlState {
    companion object {
        private const val branchKey = "branch"
        private const val typeKey = "type"
        private const val valueKey = "value"
        private const val initialType  = "initial"
        private const val finalType  = "final"
        private const val branchType  = "branch"


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any>): ControlState {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val type = collection[typeKey] as String
            val index = collection[branchKey] as? Int
            val value = collection[valueKey] as? Map<String, Any>

            return when (type) {
                initialType ->
                    InitialControlState

                finalType ->
                    FinalControlState

                branchType ->
                    InternalControlState(index!!, ExecutionValue.fromCollection(value!!))

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

            is InternalControlState ->
                return mapOf(
                        typeKey to branchType,
                        branchKey to branchIndex
                )
        }
    }


    abstract fun digest(): Digest
}


object InitialControlState: ControlState() {
    override fun digest(): Digest {
        return Digest.ofUtf8(InitialControlState::class.simpleName)
    }
}


object FinalControlState: ControlState() {
    override fun digest(): Digest {
        return Digest.ofUtf8(FinalControlState::class.simpleName)
    }
}


class InternalControlState(
        val branchIndex: Int,
        val value: ExecutionValue
): ControlState() {
    override fun digest(): Digest {
        val digest = Digest.Builder()

        digest.addUtf8(InternalControlState::class.simpleName)

        digest.addInt(branchIndex)

        return digest.digest()
    }
}

