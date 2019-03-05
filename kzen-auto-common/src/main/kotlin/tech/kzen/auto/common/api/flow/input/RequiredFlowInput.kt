package tech.kzen.auto.common.api.flow.input


interface RequiredFlowInput<out T>: FlowInput<T> {
    override fun get(): T
}