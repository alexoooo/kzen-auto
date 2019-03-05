package tech.kzen.auto.common.api.flow


interface SingleFlow<out T>: DataFlow {
    interface Output<in T> {
        /**
         * must be called
         */
        fun set(payload: T)
    }

    fun onMessage(output: Output<T>)
}