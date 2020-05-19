package tech.kzen.auto.common.paradigm.common.model

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


sealed class ExecutionResult
    : Digestible
{
    companion object {
        const val errorKey = "error"
        const val valueKey = "value"
        const val detailKey = "detail"

        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any?>): ExecutionResult {
            val error = collection[errorKey]

            return if (error == null) {
                ExecutionSuccess(
                        ExecutionValue.fromJsonCollection(collection[valueKey] as Map<String, Any>),
                        ExecutionValue.fromJsonCollection(collection[detailKey] as Map<String, Any>)
                )
            }
            else {
                ExecutionFailure(error as String)
            }
        }
    }


    abstract fun toCollection(): Map<String, Any?>



    override fun digest(): Digest {
        val digest = Digest.Builder()
        digest(digest)
        return digest.digest()
    }


    override fun digest(builder: Digest.Builder) {
        when (this) {
            is ExecutionFailure -> {
                builder.addBoolean(false)
                builder.addUtf8(errorMessage)
            }

            is ExecutionSuccess -> {
                builder.addBoolean(true)
                value.digest(builder)
                detail.digest(builder)
            }
        }
    }
}


data class ExecutionFailure(
        val errorMessage: String
): ExecutionResult() {
    override fun toCollection(): Map<String, Any?> {
        return mapOf(
                errorKey to errorMessage
        )
    }
}


data class ExecutionSuccess(
        val value: ExecutionValue,
        val detail: ExecutionValue
): ExecutionResult() {
    companion object {
        val empty = ExecutionSuccess(NullExecutionValue, NullExecutionValue)

        fun ofValue(value: ExecutionValue): ExecutionSuccess {
            return ExecutionSuccess(value, detail = NullExecutionValue)
        }
    }


    override fun toCollection(): Map<String, Any?> {
        return mapOf(
                valueKey to value.toJsonCollection(),
                detailKey to detail.toJsonCollection()
        )
    }


    override fun digest(): Digest {
        val digest = Digest.Builder()
        value.digest(digest)
        detail.digest(digest)
        return digest.digest()
    }
}
