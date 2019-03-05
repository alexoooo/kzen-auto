package tech.kzen.auto.common.api.flow.input


interface FlowInput<out T> {
    fun get(): T?
    fun index(): Long
    fun isRepeated(): Boolean
}