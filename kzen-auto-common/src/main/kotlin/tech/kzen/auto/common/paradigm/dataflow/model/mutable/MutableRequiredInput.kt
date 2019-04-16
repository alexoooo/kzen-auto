package tech.kzen.auto.common.paradigm.dataflow.model.mutable

import tech.kzen.auto.common.paradigm.dataflow.input.RequiredInput


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