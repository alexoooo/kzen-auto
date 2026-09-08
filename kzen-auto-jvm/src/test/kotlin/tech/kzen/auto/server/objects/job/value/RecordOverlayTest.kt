package tech.kzen.auto.server.objects.job.value

import org.junit.Test
import tech.kzen.auto.server.objects.job.worker.preview.PreviewCapture
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.data.type.*
import tech.kzen.lib.common.exec.data.value.*
import kotlin.test.*


class RecordOverlayTest {
    data class Day(val open: Boolean, val prices: List<Long>, val tags: Map<String, String>)
    data class Item(val date: String, val symbol: String, val sourceUrl: String, val day: Day, val note: String?)
    data class Link(val name: String, var next: Link? = null)
    private val testField = FieldId("test")
    private val integer = DataType.Scalar(ScalarKind.Integer(32))
    private fun calculated(name: String = "test", value: Long = 4) =
        CalculatedFieldValue(FieldId(name), integer, LongExecutionValue(value))

    @Test
    fun appendsWithoutReadingOrFlatteningNativeChildrenAndMatchesDeclaredContract() {
        DefaultDataAdapterRegistry().use { registry ->
            val native = Item("2019-12-30", "AAPL", "https://example.test/itch", Day(true, listOf(12L), mapOf("a" to "b")), null)
            val source = registry.lift(native)
            val result = FormulaValueTransformer.transform(JobValueClaim(source, true), { listOf(calculated()) }).value
            assertEquals(RecordOverlay.appendContract(source.contract, listOf(DataField(testField, integer))), result.contract)
            assertEquals(listOf("date", "symbol", "sourceUrl", "day", "note", "test").toSet(),
                (result.type as DataType.Record).fields.map { it.id.name }.toSet())
            assertSame(native, result.access.native(result.root))
            val day = result.access.field(result.root, FieldId("day"))
            assertSame(native.day, result.access.native(day))
            val prices = result.access.field(day, FieldId("prices"))
            assertEquals(12L, result.access.readLong(result.access.element(prices, 0)))
            val tags = result.access.field(day, FieldId("tags"))
            assertEquals("b", result.access.readText(result.access.entry(tags, tech.kzen.lib.common.exec.TextExecutionValue("a"))))
            assertEquals(DataState.Null, result.access.state(result.access.field(result.root, FieldId("note"))))
            assertEquals(4L, result.access.readLong(result.access.field(result.root, testField)))
            assertEquals(source.contract.nativeByPath, result.contract.nativeByPath)
            assertEquals(5, (source.type as DataType.Record).fields.size)
        }
    }

    @Test
    fun chainedFormulasReadAddedAndOriginalFieldsAndKeepNativeIdentity() {
        DefaultDataAdapterRegistry().use { registry ->
            val native = Item("d", "AAPL", "u", Day(true, emptyList(), emptyMap()), null)
            val source = registry.lift(native)
            val first = RecordOverlay.append(source, listOf(calculated()))
            val second = FormulaValueTransformer.transform(JobValueClaim(first, true), { projection ->
                val index = (0 until projection.size).single { projection.field(it) == testField }
                listOf(calculated("next", projection.readLong(index) + 1))
            }).value
            assertSame(native, second.access.native(second.root))
            assertEquals("AAPL", second.access.readText(second.access.field(second.root, FieldId("symbol"))))
            assertEquals(5L, second.access.readLong(second.access.field(second.root, FieldId("next"))))
        }
    }

    @Test
    fun carryRenamesStructuredFieldsAndTheirNativeMetadataInSourceOrder() {
        DefaultDataAdapterRegistry().use { registry ->
            val native = Item("d", "AAPL", "u", Day(true, listOf(1), emptyMap()), null)
            val source = RecordOverlay.append(registry.lift(native), listOf(calculated()))
            val target = LiteralDataValues.lift(recordOf("message" to "done"))
            val selection = CarrySelection.Selected(listOf(CarriedField(testField), CarriedField(FieldId("day"), FieldId("originalDay"))))
            val result = RecordOverlay.carry(target, source, selection)
            assertEquals(listOf("message", "originalDay", "test"), (result.type as DataType.Record).fields.map { it.id.name })
            assertEquals(RecordOverlay.carryContract(target.contract, source.contract, selection), result.contract)
            val child = result.access.field(result.root, FieldId("originalDay"))
            assertSame(native.day, result.access.native(child))
            assertEquals(source.contract.child(DataPathSegment.Field(FieldId("day"))),
                result.contract.child(DataPathSegment.Field(FieldId("originalDay"))))
            assertFailsWith<IllegalArgumentException> { RecordOverlay.carry(source, source, CarrySelection.All()) }
            assertFailsWith<IllegalArgumentException> { RecordOverlay.carry(target, source,
                CarrySelection.Selected(listOf(CarriedField(FieldId("missing"))))) }
        }
    }

    @Test
    fun recursiveNativeContractAndCycleSurviveAppendAndPreview() {
        DefaultDataAdapterRegistry().use { registry ->
            val link = Link("root").also { it.next = it }
            val source = registry.lift(link)
            val result = RecordOverlay.append(source, listOf(calculated()))
            assertEquals(source.contract.definitions, result.contract.definitions)
            assertEquals(source.contract.definitionNatives, result.contract.definitionNatives)
            val child = result.access.field(result.root, FieldId("next"))
            assertSame(link, result.access.native(child))
            assertSame(link, result.access.native(result.access.field(child, FieldId("next"))))
            assertTrue(PreviewCapture().capture(result).toString().contains("cycle", ignoreCase = true))
        }
    }

    @Test
    fun absentFieldsDelegateTheirStateAndCalculatedNullRemainsNull() {
        val original = LiteralDataValues.lift(recordOf("name" to "a"))
        val access = object: ValueAccess by original.access {
            override fun state(node: DataNode) = if (node == original.root) DataState.Present else DataState.Absent
        }
        val result = RecordOverlay.append(DataValue(access, original.root), listOf(
            CalculatedFieldValue(testField, integer.copy(nullable = true), null, DataState.Null)))
        assertEquals(DataState.Absent, result.access.state(result.access.field(result.root, FieldId("name"))))
        assertEquals(DataState.Null, result.access.state(result.access.field(result.root, testField)))
        assertFailsWith<IllegalArgumentException> { RecordOverlay.append(result, listOf(calculated())) }
    }
}
