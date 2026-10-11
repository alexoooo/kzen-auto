package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.recordOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame


/**
 * [ColumnProjections] reads each value as [JobDataValues.projection] does, finding the descriptor once per payload
 * contract object and, for a flat record, each column's node once.
 */
class ColumnProjectionsTest {
    private val text = DataType.Scalar(ScalarKind.Text)
    private val contract = DataContract(DataType.Record(listOf(
        DataField(FieldId("name"), text),
        DataField(FieldId("value"), DataType.Scalar(ScalarKind.Integer(64))))))
    private val header = FlatRecordHeader(contract)


    @Test
    fun rowsOfOneHeaderShareTheDescriptorAndReadAsByName() {
        val projections = ColumnProjections()

        val first = projections.of(flat("alpha", "1"))
        val second = projections.of(flat("beta", "2"))

        assertSame(first.descriptor, second.descriptor)
        val byName = JobDataValues.projection(second.value)
        for (index in 0 until byName.size) {
            assertEquals(byName.node(index), second.node(index))
            assertEquals(byName.render(index), second.render(index))
        }
        assertEquals("beta", second.readText(0))
        assertEquals(2L, second.readLong(1))
    }


    @Test
    fun anAbsentFieldOfAProjectedRowReadsAsAbsent() {
        val projections = ColumnProjections()
        projections.of(flat("alpha", "1"))

        val record = FlatFileRecord.of("beta", "")
        val projected = projections.of(
            JobDataValues.projectedRecord(header, record, listOf(DataState.Present, DataState.Absent)))

        assertEquals(DataState.Absent, projected.state(1))
        assertEquals(ColumnProjection.missingText, projected.render(1))
    }


    @Test
    fun anotherContractOrAnObjectRowIsReadThroughItsOwnColumns() {
        val projections = ColumnProjections()
        projections.of(flat("alpha", "1"))

        val other = DataContract(DataType.Record(listOf(DataField(FieldId("city"), text))))
        val city = FlatFileRecord.of("Toronto")
        city.attachHeader(FlatRecordHeader(other))
        val flatCity = projections.of(DataValue(city, DataNode(0)))
        val objectCity = projections.of(LiteralDataValues.lift(recordOf("city" to "Ottawa"), other))

        assertEquals(listOf(FieldId("city")), flatCity.descriptor.columns.map { it.field })
        assertEquals("Toronto", flatCity.render(0))
        assertEquals("Ottawa", objectCity.render(0))
    }


    private fun flat(vararg cells: String): DataValue {
        val record = FlatFileRecord.of(*cells)
        record.attachHeader(header)
        return DataValue(record, DataNode(0))
    }
}
