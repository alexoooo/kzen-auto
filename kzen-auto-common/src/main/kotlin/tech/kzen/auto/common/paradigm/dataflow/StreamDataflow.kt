package tech.kzen.auto.common.paradigm.dataflow


interface StreamDataflow: Dataflow {
    /**
     * If using a StreamOutput, will be invoked to get next.
     */
    fun next()
}