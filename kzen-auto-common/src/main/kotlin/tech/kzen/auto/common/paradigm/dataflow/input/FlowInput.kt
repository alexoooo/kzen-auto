package tech.kzen.auto.common.paradigm.dataflow.input


interface FlowInput<out T> {
    fun get(): T?
//    fun index(): Long
//    fun isRepeated(): Boolean
}