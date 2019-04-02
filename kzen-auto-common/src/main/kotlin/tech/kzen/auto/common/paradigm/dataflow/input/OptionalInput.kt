package tech.kzen.auto.common.paradigm.dataflow.input


interface OptionalInput<out T> {
    /**
     * @return current received message payload (if any)
     */
    fun get(): T?
}