package tech.kzen.auto.client.util


sealed class ClientResult<T> {
    companion object {
        fun <T> ofSuccess(value: T): ClientSuccess<T> {
            return ClientSuccess(value)
        }

        fun <T> ofError(message: String): ClientError<T> {
            return ClientError(message)
        }
    }

    fun valueOrNull(): T? {
        return (this as? ClientSuccess)?.value
    }

    fun errorOrNull(): String? {
        return (this as? ClientError)?.message
    }
}


data class ClientSuccess<T>(val value: T): ClientResult<T>()


data class ClientError<T>(val message: String): ClientResult<T>()