package tech.kzen.auto.common.paradigm.imperative.model.control

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


//---------------------------------------------------------------------------------------------------------------------
sealed class ControlState
    : Digestible
{
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
                    FinalControlState(ExecutionValue.fromCollection(value!!))

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
                mapOf(typeKey to finalType,
                        valueKey to value.toCollection())

            is InternalControlState ->
                return mapOf(
                        typeKey to branchType,
                        branchKey to branchIndex,
                        valueKey to value.toCollection()
                )
        }
    }


    override fun digest(): Digest {
        val digest = Digest.Builder()
        digest(digest)
        return digest.digest()
    }


    override fun digest(builder: Digest.Builder) {
        when (this) {
            is InitialControlState -> {
                builder.addInt(0)
            }

            is FinalControlState -> {
                builder.addInt(1)
                value.digest(builder)
            }

            is InternalControlState -> {
                builder.addInt(2)
                builder.addInt(branchIndex)
                value.digest(builder)
            }
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
object InitialControlState: ControlState() {
    override fun digest(): Digest {
        return Digest.empty
    }
}


data class FinalControlState(
        val value: ExecutionValue
): ControlState() {
    override fun digest(): Digest {
        return value.digest()
    }
}


//---------------------------------------------------------------------------------------------------------------------
class InternalControlState(
        val branchIndex: Int,
        val value: ExecutionValue
): ControlState() {
    override fun digest(): Digest {
        val digest = Digest.Builder()

        digest.addInt(branchIndex)
        digest.addDigestible(value)

        return digest.digest()
    }
}

