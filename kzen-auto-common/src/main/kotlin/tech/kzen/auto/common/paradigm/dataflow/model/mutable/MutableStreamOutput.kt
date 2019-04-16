package tech.kzen.auto.common.paradigm.dataflow.model.mutable

import tech.kzen.auto.common.paradigm.dataflow.output.StreamOutput


class MutableStreamOutput<T>: StreamOutput<T> {
    private var payload: T? = null
    private var hasNext: Boolean = false


    override fun set(payload: T) {
        this.payload = payload
        hasNext = false
    }


    override fun set(payload: T, hasNext: Boolean) {
        this.payload = payload
        this.hasNext = false
    }


    fun getAndClear(): T? {
        val value = payload
        payload = null
        return value
    }
}