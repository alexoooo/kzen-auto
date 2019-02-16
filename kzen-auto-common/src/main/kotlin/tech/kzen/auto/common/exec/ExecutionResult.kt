package tech.kzen.auto.common.exec

import tech.kzen.lib.common.util.Digest


sealed class ExecutionResult {
    companion object {
        const val errorKey = "error"
        const val valueKey = "value"
        const val detailKey = "detail"

        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any>): ExecutionResult {
            val error = collection[errorKey]

            return if (error == null) {
                ExecutionSuccess(
                        ExecutionValue.fromCollection(collection[valueKey] as Map<String, Any>),
                        ExecutionValue.fromCollection(collection[detailKey] as Map<String, Any>)
                )
            }
            else {
                ExecutionError(error as String)
            }
        }
    }


    abstract fun toCollection(): Map<String, Any?>

    abstract fun digest(): Digest
}


data class ExecutionError(
        val errorMessage: String
): ExecutionResult() {
    override fun toCollection(): Map<String, Any?> {
        return mapOf(
                errorKey to errorMessage
        )
    }

    override fun digest(): Digest {
        return Digest.ofXoShiRo256StarStar(errorMessage)
    }
}


data class ExecutionSuccess(
        val value: ExecutionValue,
        val detail: ExecutionValue
): ExecutionResult() {
    companion object {
        val empty = ExecutionSuccess(NullExecutionValue, NullExecutionValue)
    }

    override fun toCollection(): Map<String, Any?> {
        return mapOf(
                valueKey to value.toCollection(),
                detailKey to detail.toCollection()
        )
    }

    override fun digest(): Digest {
        val digest = Digest.Streaming()
        value.digest(digest)
        detail.digest(digest)
        return digest.digest()
    }
}

