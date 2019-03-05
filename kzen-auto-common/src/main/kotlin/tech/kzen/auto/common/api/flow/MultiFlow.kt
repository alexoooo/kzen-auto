package tech.kzen.auto.common.api.flow


interface MultiFlow<out T>: DataFlow {
    interface Output<in T> {
        /**
         * called zero or more times
         */
        fun add(payload: T)
    }

    fun onMessage(output: Output<T>)
}