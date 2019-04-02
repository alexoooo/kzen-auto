package tech.kzen.auto.common.paradigm.dataflow.output


interface BatchEgress<in T>: OptionalEgress<T> {
    /**
     * can be called any number of times, will be buffered
     */
    fun add(payload: T)
}