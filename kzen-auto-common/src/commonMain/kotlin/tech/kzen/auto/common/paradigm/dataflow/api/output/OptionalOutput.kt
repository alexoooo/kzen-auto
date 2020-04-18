package tech.kzen.auto.common.paradigm.dataflow.api.output


interface OptionalOutput<in T> {
    /**
     * Must be called at most (and in some cases exactly) one time.
     */
    fun set(payload: T)
}