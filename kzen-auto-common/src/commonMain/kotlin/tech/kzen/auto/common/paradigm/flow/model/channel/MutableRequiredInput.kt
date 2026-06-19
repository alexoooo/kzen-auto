package tech.kzen.auto.common.paradigm.flow.model.channel

import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput


// TODO: enforce optional/required contracts
class MutableRequiredInput<out T>: RequiredInput<T>, MutableInput<T> {
    private var value: T? = null


    override fun get(): T {
        return value
                ?: throw NoSuchElementException("required input missing")
    }


    override fun set(value: @UnsafeVariance T) {
        this.value = value
    }


    override fun clear() {
        value = null
    }
}