package tech.kzen.auto.common.paradigm.dataflow.output


interface BatchOutput<in T>: OptionalOutput<T> {
    /**
     * can be called any number of times, will be buffered
     */
    fun add(payload: T)
}