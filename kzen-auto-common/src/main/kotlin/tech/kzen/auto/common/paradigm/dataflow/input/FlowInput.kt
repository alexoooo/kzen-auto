package tech.kzen.auto.common.paradigm.dataflow.input


interface FlowInput<out T>: OptionalFlowInput<T> {
    /**
     * @return current received message payload
     */
    override fun get(): T
}