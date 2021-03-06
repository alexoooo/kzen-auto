package tech.kzen.auto.common.paradigm.dataflow.api.input


interface RequiredInput<out T>: OptionalInput<T> {
    /**
     * @return current received message payload
     */
    override fun get(): T
}