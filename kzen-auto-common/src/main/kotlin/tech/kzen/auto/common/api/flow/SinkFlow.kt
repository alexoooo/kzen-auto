package tech.kzen.auto.common.api.flow


interface SinkFlow: DataFlow {
    fun onMessage()
}