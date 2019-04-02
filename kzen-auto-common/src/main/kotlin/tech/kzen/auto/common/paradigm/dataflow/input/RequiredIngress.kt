package tech.kzen.auto.common.paradigm.dataflow.input


interface RequiredIngress<out T>: OptionalIngress<T> {
    /**
     * @return current received message payload
     */
    override fun get(): T
}