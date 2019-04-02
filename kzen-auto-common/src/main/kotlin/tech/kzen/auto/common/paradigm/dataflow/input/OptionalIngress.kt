package tech.kzen.auto.common.paradigm.dataflow.input


interface OptionalIngress<out T> {
    /**
     * @return current received message payload (if any)
     */
    fun get(): T?
}