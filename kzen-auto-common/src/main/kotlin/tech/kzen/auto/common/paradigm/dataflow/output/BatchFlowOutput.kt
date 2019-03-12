package tech.kzen.auto.common.paradigm.dataflow.output


interface BatchFlowOutput<in T>: OptionalFlowOutput<T> {
    fun add(payload: T)
}