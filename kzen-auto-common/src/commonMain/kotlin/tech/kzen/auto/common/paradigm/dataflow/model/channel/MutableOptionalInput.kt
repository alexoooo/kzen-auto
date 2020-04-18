package tech.kzen.auto.common.paradigm.dataflow.model.channel

import tech.kzen.auto.common.paradigm.dataflow.api.input.OptionalInput



// TODO: enforce optional/required contracts
class MutableOptionalInput<out T>: OptionalInput<T>, MutableInput<T> {
    private var value: T? = null


    override fun get(): T? {
        return value
    }


    override fun set(value: @UnsafeVariance T) {
        this.value = value
    }


    override fun clear() {
        value = null
    }
}