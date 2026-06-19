package tech.kzen.auto.common.paradigm.flow.api.output


interface StreamOutput<in T>: OptionalOutput<T> {
    /**
     * If hasNext, then StreamFlowVertex.next() is called
     */
    fun set(payload: T, hasNext: Boolean)
}