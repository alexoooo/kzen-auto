package tech.kzen.auto.common.paradigm.flow.api.input


interface OptionalInput<out T> {
    /**
     * @return current received message payload (if any)
     */
    fun get(): T?
}