package tech.kzen.auto.common.paradigm.dataflow.api.output


interface RequiredOutput<in T>: OptionalOutput<T> {
    /**
     * Must be called exactly one time
     */
    override fun set(payload: T)
}