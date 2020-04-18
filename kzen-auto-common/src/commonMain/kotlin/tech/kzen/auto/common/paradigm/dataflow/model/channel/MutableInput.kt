package tech.kzen.auto.common.paradigm.dataflow.model.channel

interface MutableInput<out T> {
    fun set(value: @UnsafeVariance T)

    fun clear()
}