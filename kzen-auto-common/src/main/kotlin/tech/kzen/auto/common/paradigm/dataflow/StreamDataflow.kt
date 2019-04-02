package tech.kzen.auto.common.paradigm.dataflow


interface StreamDataflow: Dataflow {
    /**
     * If using a StreamEgress, will be invoked to get next.
     */
    fun next()
}