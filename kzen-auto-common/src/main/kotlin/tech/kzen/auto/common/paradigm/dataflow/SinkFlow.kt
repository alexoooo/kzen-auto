package tech.kzen.auto.common.paradigm.dataflow


interface SinkFlow: DataFlow {
    fun onMessage()
}