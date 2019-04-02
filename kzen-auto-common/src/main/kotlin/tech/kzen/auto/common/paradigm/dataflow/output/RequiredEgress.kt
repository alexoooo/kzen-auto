package tech.kzen.auto.common.paradigm.dataflow.output


interface RequiredEgress<in T>: OptionalEgress<T> {
    /**
     * Must be called exactly one time
     */
    override fun set(payload: T)
}