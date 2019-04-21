package tech.kzen.auto.common.paradigm.dataflow.api.output


interface StreamOutput<in T>: OptionalOutput<T> {
    /**
     * If hasNext, then StreamDataflow.next() is called
     */
    fun set(payload: T, hasNext: Boolean)
}