package tech.kzen.auto.common.paradigm.dataflow.model.chanel

import tech.kzen.auto.common.paradigm.dataflow.api.input.RequiredInput



// TODO: enforce optional/required contracts
class MutableRequiredInput<out T>: RequiredInput<T> {
    private var value: T? = null


    override fun get(): T {
        return value!!
    }


    fun set(value: @UnsafeVariance T) {
        this.value = value
    }


    fun clear() {
        value = null
    }
}