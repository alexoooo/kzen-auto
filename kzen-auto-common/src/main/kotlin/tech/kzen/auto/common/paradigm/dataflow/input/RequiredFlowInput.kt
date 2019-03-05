package tech.kzen.auto.common.paradigm.dataflow.input


interface RequiredFlowInput<out T>: FlowInput<T> {
    override fun get(): T
}