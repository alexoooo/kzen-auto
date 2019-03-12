package tech.kzen.auto.common.paradigm.dataflow.output


interface SingleFlowOutput<in T>: OptionalFlowOutput<T> {
    /**
     * Must be called at exactly one time
     */
    override fun set(payload: T)
}