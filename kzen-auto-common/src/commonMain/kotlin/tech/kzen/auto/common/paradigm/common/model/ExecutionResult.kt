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
        fun fromJsonCollection(collection: Map<String, Any?>): ExecutionResult {
            val error = collection[errorKey]

            return if (error == null) {
                ExecutionSuccess.fromJsonCollection(collection)
            }
            else {
                ExecutionFailure(error as String)
            }
        }


        fun success(
            value: ExecutionValue = NullExecutionValue,
            detail: ExecutionValue = NullExecutionValue
        ): ExecutionSuccess {
            return ExecutionSuccess(value, detail)
        }


        fun failure(
            message: String
        ): ExecutionFailure {
            return ExecutionFailure(message)
        }
    }


    abstract fun toJsonCollection(): Map<String, Any?>


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
    companion object {
        fun ofException(throwable: Throwable): ExecutionFailure {
            return ofException("", throwable)
        }

        fun ofException(userMessage: String, throwable: Throwable): ExecutionFailure {
            val errorName = throwable::class
                .simpleName
                ?.removeSuffix("Exception")
                ?.replace(Regex("([A-Z])"), " $1")
                ?.trim()

            val message = throwable.message

            val fullMessage =
                if (errorName != null) {
                    if (message != null) {
                        "$errorName: $message"
                    }
                    else {
                        errorName
                    }
                }
                else {
                    message ?: "exception"
                }

            return ExecutionFailure(userMessage + fullMessage)
        }
    }


    override fun toJsonCollection(): Map<String, Any?> {
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


        @Suppress("UNCHECKED_CAST")
        fun fromJsonCollection(collection: Map<String, Any?>): ExecutionSuccess {
            return ExecutionSuccess(
                ExecutionValue.fromJsonCollection(collection[valueKey] as Map<String, Any>),
                ExecutionValue.fromJsonCollection(collection[detailKey] as Map<String, Any>)
            )
        }
    }


    fun withDetail(detail: ExecutionValue): ExecutionSuccess {
        return ExecutionSuccess(value, detail)
    }


    override fun toJsonCollection(): Map<String, Any?> {
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
