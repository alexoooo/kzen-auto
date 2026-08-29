package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.recordOf
import kotlin.test.Test
import kotlin.test.assertEquals


class FlatRecordValueIntegrationTest {
    private data class Reading(val sensor: String, val value: Double)

    private val sensor = FieldId("sensor")
    private val reading = FieldId("value")
    private val contract = DataContract(DataType.Record(listOf(
        DataField(sensor, DataType.Scalar(ScalarKind.Text)),
        DataField(reading, DataType.Scalar(ScalarKind.Floating(64))))))


    @Test
    fun literalNativeAndFlatBackingsUseIdenticalTypedTraversal() {
        val literal = LiteralDataValues.lift(
            recordOf("sensor" to "north", "value" to 12.5),
            contract)
        val flatRecord = FlatFileRecord.of("north", "12.5")
        flatRecord.attachHeader(FlatRecordHeader(contract))
        val flat = DataValue(flatRecord, DataNode(0))

        DefaultDataAdapterRegistry().use { registry ->
            val native = registry.lift(Reading("north", 12.5))
            listOf(literal, native, flat).forEach(::assertReading)
        }
    }


    private fun assertReading(value: DataValue) {
        assertEquals("north", value.access.readText(value.access.field(value.root, sensor)))
        assertEquals(12.5, value.access.readDouble(value.access.field(value.root, reading)))
    }
}
