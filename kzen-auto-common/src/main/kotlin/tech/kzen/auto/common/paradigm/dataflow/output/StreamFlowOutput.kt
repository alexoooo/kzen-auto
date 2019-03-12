package tech.kzen.auto.common.paradigm.dataflow.output


interface StreamFlowOutput<in T>: OptionalFlowOutput<T> {
    /**
     * If hasNext, then StreamDataFlow.next() is called
     */
    fun set(payload: T, hasNext: Boolean)
}