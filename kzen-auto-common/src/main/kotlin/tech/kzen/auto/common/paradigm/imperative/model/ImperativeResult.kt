package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.lib.common.util.Digest


sealed class ImperativeResult {
    companion object {
        const val errorKey = "error"
        const val valueKey = "value"
        const val detailKey = "detail"

        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any?>): ImperativeResult {
            val error = collection[errorKey]

            return if (error == null) {
                ImperativeSuccess(
                        ExecutionValue.fromCollection(collection[valueKey] as Map<String, Any>),
                        ExecutionValue.fromCollection(collection[detailKey] as Map<String, Any>)
                )
            }
            else {
                ImperativeError(error as String)
            }
        }
    }


    abstract fun toCollection(): Map<String, Any?>

    abstract fun digest(): Digest
}


data class ImperativeError(
        val errorMessage: String
): ImperativeResult() {
    override fun toCollection(): Map<String, Any?> {
        return mapOf(
                errorKey to errorMessage
        )
    }

    override fun digest(): Digest {
        return Digest.ofXoShiRo256StarStar(errorMessage)
    }
}


data class ImperativeSuccess(
        val value: ExecutionValue,
        val detail: ExecutionValue
): ImperativeResult() {
    companion object {
        val empty = ImperativeSuccess(NullExecutionValue, NullExecutionValue)
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

