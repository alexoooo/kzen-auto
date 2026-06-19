package tech.kzen.auto.common.paradigm.flow.model.channel

interface MutableInput<out T> {
    fun set(value: @UnsafeVariance T)

    fun clear()
}