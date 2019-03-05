package tech.kzen.auto.common.paradigm.dataflow


interface OptionalFlow<out T>: DataFlow {
    interface Output<in T> {
        /**
         * may or may not be called
         */
        fun set(payload: T)
    }

    fun onMessage(output: Output<T>)
}