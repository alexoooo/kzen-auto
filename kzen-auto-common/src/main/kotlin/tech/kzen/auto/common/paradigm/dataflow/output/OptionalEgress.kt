package tech.kzen.auto.common.paradigm.dataflow.output


interface OptionalEgress<in T> {
    /**
     * Must be called at most (and in some cases exactly) one time.
     */
    fun set(payload: T)
}