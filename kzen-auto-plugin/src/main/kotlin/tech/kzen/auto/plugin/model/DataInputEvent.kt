package tech.kzen.auto.plugin.model


abstract class DataInputEvent {
    val data = RecordDataBuffer()

    var index: Long = 0
    var endOfData: Boolean = false
}