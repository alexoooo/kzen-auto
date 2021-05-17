package tech.kzen.auto.plugin.model


abstract class DataInputEvent {
    val data = RecordDataBuffer()
    var endOfData: Boolean = false
}