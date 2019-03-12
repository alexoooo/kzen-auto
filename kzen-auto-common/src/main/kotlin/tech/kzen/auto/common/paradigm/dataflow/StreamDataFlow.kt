package tech.kzen.auto.common.paradigm.dataflow


interface StreamDataFlow: DataFlow {
    /**
     * If using a StreamFlowOutput, will be invoked to get next.
     */
    fun next()
}