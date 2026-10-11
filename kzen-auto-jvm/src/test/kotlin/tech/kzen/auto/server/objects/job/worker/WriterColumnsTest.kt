package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.path.ColumnPatterns
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec.WriterColumn
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.format.FormatCells
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


/** A `*` that keeps only the payload columns its patterns allow, under the names it gives them. */
class WriterColumnsTest {
    private val text = DataType.Scalar(ScalarKind.Text)
    private val payload = DataContract(DataType.Record(listOf(
        DataField(FieldId("c0"), text),
        DataField(FieldId("c1"), text),
        DataField(FieldId("tmp_a"), text))))

    private val narrowed = WriterColumnSpec(listOf(WriterColumn.PayloadFields(
        ColumnPatterns(listOf(), listOf("c0", "tmp_.*")),
        mapOf("c1" to "value"))))


    @Test
    fun theHeaderNamesOnlyTheKeptColumnsAsRenamed() {
        assertEquals(listOf("value"), WriterColumns(narrowed).header(payload) { it.render() })
        assertEquals(
            listOf("c0", "c1", "tmp_a"),
            WriterColumns(WriterColumnSpec.payloadFields).header(payload) { it.render() })
    }


    @Test
    fun eachRowWritesOnlyTheKeptFields() {
        val columns = WriterColumns(narrowed)
        val row = WriterColumns.Row(JobDataValues.projection(record("a", "b", "c")), null)
        assertEquals(listOf("b"), columns.fields(row))
    }


    @Test
    fun formatCellsReadOnlyTheKeptFields() {
        val cells = FormatCells(WriterColumns(narrowed), payload)
        cells.bind(record("a", "b", "c"))
        assertEquals(1, cells.size)
        assertEquals("b", cells.text(0).toString())
    }


    @Test
    fun aPatternThatDoesNotCompileIsRefusedBeforeRun() {
        val broken = WriterColumnSpec(listOf(WriterColumn.PayloadFields(ColumnPatterns(listOf("("), listOf()))))
        val error = WriterColumns(broken).staticError(payload)
        assertEquals(true, error?.startsWith("Columns: Allow 1: "), error)
    }


    @Test
    fun keepingNoColumnIsRefusedBeforeRun() {
        val nothing = WriterColumnSpec(listOf(WriterColumn.PayloadFields(ColumnPatterns(listOf(), listOf(".*")))))
        assertEquals(
            "Columns: no payload column is kept (check Allow and Exclude)",
            WriterColumns(nothing).staticError(payload))
        assertNull(WriterColumns(narrowed).staticError(payload))
    }


    private fun record(vararg values: String): DataValue {
        val record = FlatFileRecord.of(values.toList())
        record.attachHeader(FlatRecordHeader(payload))
        return DataValue(record, DataNode(0))
    }
}
