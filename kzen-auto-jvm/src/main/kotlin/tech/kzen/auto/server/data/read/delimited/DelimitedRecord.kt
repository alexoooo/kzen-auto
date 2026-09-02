package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueAccess


data class DelimitedRecord(
    val backing: FlatFileRecord,
    val access: ValueAccess,
    val value: DataValue
)
