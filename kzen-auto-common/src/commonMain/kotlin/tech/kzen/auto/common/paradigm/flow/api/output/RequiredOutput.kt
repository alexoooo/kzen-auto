package tech.kzen.auto.common.paradigm.flow.api.output


interface RequiredOutput<in T>: OptionalOutput<T> {
    /**
     * Must be called exactly one time
     */
    override fun set(payload: T)
}