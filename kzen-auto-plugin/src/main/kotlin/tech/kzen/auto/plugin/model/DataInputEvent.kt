package tech.kzen.auto.plugin.model

import tech.kzen.auto.plugin.model.data.DataRecordBuffer


abstract class DataInputEvent {
    val data = DataRecordBuffer()
    var endOfData: Boolean = false
}