package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.recordOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue


class ColumnProjectionTest {
    @Test
    fun recordDescriptorAndBoundProjectionAgreeWithoutFlatteningNestedFields() {
        val first = FieldId("reading", 0)
        val second = FieldId("reading", 1)
        val nested = FieldId("location")
        val contract = DataContract(DataType.Record(listOf(
            DataField(first, DataType.Scalar(ScalarKind.Text)),
            DataField(second, DataType.Scalar(ScalarKind.Integer(64))),
            DataField(nested, DataType.Record(listOf(
                DataField(FieldId("city"), DataType.Scalar(ScalarKind.Text))))))))
        val record = FlatFileRecord.of("13.0", "13", "Toronto")
        record.attachHeader(FlatRecordHeader(contract))
        val value = DataValue(record, DataNode(0))

        val descriptor = ColumnProjectionDescriptor.from(contract)
        val projection = descriptor.bind(value)

        assertEquals(listOf(first, second, nested), descriptor.columns.map { it.field })
        assertEquals(listOf("reading", "reading (2)", "location"),
            descriptor.columns.map { it.label.render() })
        assertEquals(descriptor.columns.map { it.field },
            (0 until projection.size).map { projection.field(it) })
        assertEquals("13.0", projection.readText(0))
        assertEquals(13L, projection.readLong(1))
        assertIs<DataType.Record>(projection.contract(2).structural)
        assertEquals(3, projection.size, "nested fields are not flattened implicitly")
    }

    @Test
    fun optionalAbsenceIsRenderedOnlyByProjectionPolicy() {
        val required = FieldId("required")
        val optional = FieldId("optional")
        val contract = DataContract(DataType.Record(listOf(
            DataField(required, DataType.Scalar(ScalarKind.Text)),
            DataField(optional, DataType.Scalar(ScalarKind.Text), optional = true))))
        val value = LiteralDataValues.lift(recordOf("required" to "present"), contract)
        val projection = ColumnProjectionDescriptor.from(contract).bind(value)

        assertEquals(DataState.Absent, projection.state(1))
        assertEquals(ColumnProjection.missingText, projection.render(1))
        assertEquals(DataState.Absent, value.access.state(value.access.field(value.root, optional)))
        assertFailsWith<DataAccessException> { projection.scalar(1) }
    }

    @Test
    fun scalarUsesExplicitValueFieldAndTypedRead() {
        val contract = DataContract(DataType.Scalar(ScalarKind.Integer(64)))
        val projection = ColumnProjectionDescriptor.from(contract)
            .bind(LiteralDataValues.lift(13L, contract))

        assertEquals(FieldId("value"), projection.field(0))
        assertEquals(13L, projection.readLong(0))
        assertEquals("13", projection.render(0))
    }

    @Test
    fun nestedFieldsRequireExplicitSelection() {
        val location = FieldId("location")
        val city = FieldId("city")
        val contract = DataContract(DataType.Record(listOf(
            DataField(location, DataType.Record(listOf(DataField(city, DataType.Scalar(ScalarKind.Text))))))))
        val value = LiteralDataValues.lift(
            recordOf("location" to recordOf("city" to "Toronto")), contract)

        val default = ColumnProjectionDescriptor.from(contract)
        assertEquals(listOf(location), default.columns.map { it.field })

        val selected = ColumnProjectionDescriptor.select(contract, listOf(
            SelectedRecordColumn(city, listOf(location, city))))
            .bind(value)
        assertEquals(listOf(city), (0 until selected.size).map { selected.field(it) })
        assertEquals("Toronto", selected.readText(0))
    }

    @Test
    fun textColumnsKeepColumnValueCoercionAndInternedConstants() {
        val contract = DataContract(DataType.Scalar(ScalarKind.Text))
        val numericText = ColumnProjectionDescriptor.from(contract)
            .bind(LiteralDataValues.lift("13.0", contract))
        assertTrue(numericText.columnValue(0) eq 13)

        val zero = ColumnProjectionDescriptor.from(contract)
            .bind(LiteralDataValues.lift("0", contract))
        assertSame(zero.columnValue(0), zero.columnValue(0))
    }

    @Test
    fun mappingRequiresDeclaredKeysAndUsesTheirDeclaredOrder() {
        val contract = DataContract(DataType.Mapping(
            DataType.Scalar(ScalarKind.Text),
            DataType.Scalar(ScalarKind.Text)))
        val value = LiteralDataValues.lift(linkedMapOf("b" to "two", "a" to "one"), contract)

        assertFailsWith<DataException> { ColumnProjectionDescriptor.from(contract) }

        val descriptor = ColumnProjectionDescriptor.from(contract, MappingKeyProjection(listOf(
            MappingColumn(FieldId("a"), TextExecutionValue("a")),
            MappingColumn(FieldId("b"), TextExecutionValue("b")),
            MappingColumn(FieldId("missing"), TextExecutionValue("missing")))))
        val projection = descriptor.bind(value)
        assertEquals(listOf("a", "b", "missing"), descriptor.header.values.map { it.render() })
        assertEquals("one", projection.readText(0))
        assertEquals("two", projection.readText(1))
        assertEquals(DataState.Absent, projection.state(2))
        assertEquals(ColumnProjection.missingText, projection.render(2))
    }

    @Test
    fun opaqueAndUnselectedUnionHaveNoColumnCapability() {
        assertFailsWith<DataException> {
            ColumnProjectionDescriptor.from(DataContract(
                DataType.Opaque(),
                mapOf(tech.kzen.lib.common.exec.data.type.DataTypePath.root to
                        tech.kzen.lib.common.model.structure.metadata.TypeMetadata.any)))
        }
    }
}
