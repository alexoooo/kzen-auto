package tech.kzen.auto.common.paradigm.dataflow.output


interface StreamEgress<in T>: OptionalEgress<T> {
    /**
     * If hasNext, then StreamDataflow.next() is called
     */
    fun set(payload: T, hasNext: Boolean)
}