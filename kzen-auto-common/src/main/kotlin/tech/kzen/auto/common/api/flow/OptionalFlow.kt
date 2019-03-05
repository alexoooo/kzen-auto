package tech.kzen.auto.common.api.flow


interface OptionalFlow<out T> {
    interface Output<in T> {
        /**
         * may or may not be called
         */
        fun set(payload: T)
    }

    fun onMessage(output: Output<T>)
}