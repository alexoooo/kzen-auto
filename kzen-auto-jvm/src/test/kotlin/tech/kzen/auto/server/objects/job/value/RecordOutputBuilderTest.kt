package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.recordOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue


class RecordOutputBuilderTest {
    private data class Reading(val station: String, val value: Double)
    private data class Alert(val message: String)

    private val x = FieldId("x")
    private val calculated = FieldId("calculated")
    private val text = DataType.Scalar(ScalarKind.Text)
    private val integer = DataType.Scalar(ScalarKind.Integer(64))

    @Test
    fun exclusiveFlatTransferAppendsInPlaceAndInvalidatesSender() {
        val input = flatValue(listOf(DataField(x, text)), "before")
        val record = input.access as FlatFileRecord
        val sender = JobValueSender(input)
        val delivery = sender.publish(JobTransferConditions(
            trace = TraceAliasState.SynchronousSnapshotComplete))

        assertFailsWith<IllegalStateException> { sender.current() }
        val builder = RecordOutputBuilder.open(delivery.claim())
        builder.append(calculated, integer, DataState.Present, LongExecutionValue(7))
        val output = builder.finish()

        assertSame(record, output.access)
        assertEquals(2, record.fieldCount())
        assertEquals(7L, output.access.readLong(output.access.field(output.root, calculated)))
        assertEquals(0, builder.projectionCount)
        assertEquals(1, builder.appendCount)
        assertFailsWith<IllegalStateException> {
            builder.append(FieldId("late"), text, DataState.Present, TextExecutionValue("late"))
        }
    }

    @Test
    fun aliasFanOutAndLiveInspectionForceCopy() {
        val conditions = listOf(
            JobTransferConditions(senderRetainsAlias = true),
            JobTransferConditions(receiverCount = 2),
            JobTransferConditions(fanOut = true),
            JobTransferConditions(replayRetainsValue = true),
            JobTransferConditions(trace = TraceAliasState.LiveInspector))
        assertTrue(JobTransferConditions(trace = TraceAliasState.SynchronousSnapshotComplete).isExclusive)
        assertTrue(conditions.none { it.isExclusive })

        for (condition in conditions) {
            val input = flatValue(listOf(DataField(x, text)), "before")
            val source = input.access as FlatFileRecord
            val claim = JobValueSender(input).publish(condition).claim()
            val builder = RecordOutputBuilder.open(claim)
            builder.append(calculated, integer, DataState.Present, LongExecutionValue(9))
            val output = builder.finish()

            assertNotSame(source, output.access)
            assertEquals(1, source.fieldCount())
            assertEquals(2, (output.access as FlatFileRecord).fieldCount())
        }
    }

    @Test
    fun migrationAdoptsPhysicalDeliveryExactlyOnce() {
        val delivery = JobValueSender(flatValue(listOf(DataField(x, text)), "x"))
            .publish(JobTransferConditions())
        val migrated = delivery.migrate()
        assertFailsWith<IllegalStateException> { delivery.claim() }
        assertTrue(migrated.claim().exclusive)
        assertFailsWith<IllegalStateException> { migrated.claim() }
    }

    @Test
    fun nativeLaneProjectsOnceThenAppendsWithoutOverlayDepthAndKeepsNativeRoot() {
        val reading = Reading("north", 12.5)
        DefaultDataAdapterRegistry().use { registry ->
            var current = registry.lift(reading)
            var projections = 0
            var appends = 0
            repeat(12) { index ->
                val builder = RecordOutputBuilder.open(JobValueClaim(current, exclusive = true))
                builder.append(
                    FieldId("calculated$index"), integer, DataState.Present,
                    LongExecutionValue(index.toLong()))
                current = builder.finish()
                projections += builder.projectionCount
                appends += builder.appendCount
            }

            assertEquals(1, projections)
            assertEquals(12, appends)
            assertSame(reading, current.access.native(current.root))
            assertEquals("north", current.access.readText(
                current.access.field(current.root, FieldId("station"))))
            assertEquals(11L, current.access.readLong(
                current.access.field(current.root, FieldId("calculated11"))))
        }
    }

    @Test
    fun formulaWidenReplaceAndCarryAreOneOrderedValue() {
        val widened = FormulaValueTransformer.transform(
            JobValueClaim(flatValue(listOf(DataField(x, text)), "source"), exclusive = true),
            calculate = { projection ->
                listOf(CalculatedFieldValue(
                    calculated, integer,
                    LongExecutionValue(projection.readText(0).length.toLong())))
            })
        assertEquals(listOf(x, calculated), fields(widened.value))

        var replacementSawOriginalOnly = false
        val replaced = FormulaValueTransformer.transform(
            JobValueClaim(flatValue(listOf(DataField(x, text)), "source"), exclusive = true),
            calculate = { listOf(CalculatedFieldValue(calculated, integer, LongExecutionValue(6))) },
            replace = { original ->
                replacementSawOriginalOnly = original.size == 1 && original.field(0) == x
                literalAlert("replacement")
            })
        assertTrue(replacementSawOriginalOnly)
        assertEquals(listOf(FieldId("message")), fields(replaced.value))

        val carried = FormulaValueTransformer.transform(
            JobValueClaim(flatValue(listOf(DataField(x, text)), "source"), exclusive = true),
            calculate = { listOf(CalculatedFieldValue(calculated, integer, LongExecutionValue(6))) },
            replace = { literalAlert("replacement") },
            carry = CarrySelection.All())
        assertEquals(listOf(FieldId("message"), x, calculated), fields(carried.value))
        assertEquals("replacement", carried.value.access.readText(
            carried.value.access.field(carried.value.root, FieldId("message"))))
    }

    @Test
    fun selectedCarryUsesSourceOrderAndRequiresRenameForCollision() {
        val source = flatValue(
            listOf(DataField(x, text), DataField(FieldId("tail"), text)),
            "x", "tail")
        val selected = FormulaValueTransformer.transform(
            JobValueClaim(source, exclusive = true),
            calculate = { listOf(CalculatedFieldValue(calculated, integer, LongExecutionValue(2))) },
            replace = { literalAlert("replacement") },
            carry = CarrySelection.Selected(listOf(
                CarriedField(calculated, FieldId("renamed")),
                CarriedField(x))))
        assertEquals(
            listOf(FieldId("message"), x, FieldId("renamed")),
            fields(selected.value))

        assertFailsWith<IllegalArgumentException> {
            FormulaValueTransformer.transform(
                JobValueClaim(flatValue(listOf(DataField(x, text)), "source"), exclusive = true),
                calculate = { emptyList() },
                replace = { LiteralDataValues.lift(recordOf("x" to "replacement")) },
                carry = CarrySelection.All())
        }
    }

    @Test
    fun senderRetentionIsExplicit() {
        val input = flatValue(listOf(DataField(x, text)), "source")
        val sender = JobValueSender(input)
        val delivery = sender.publish(JobTransferConditions(senderRetainsAlias = true))
        assertSame(input, sender.current())
        assertFalse(delivery.claim().exclusive)
    }

    private fun flatValue(fields: List<DataField>, vararg values: String): DataValue {
        val contract = DataContract(DataType.Record(fields))
        val record = FlatFileRecord.of(*values)
        record.attachHeader(FlatRecordHeader(contract))
        return DataValue(record, DataNode(0))
    }

    private fun literalAlert(message: String): DataValue {
        val contract = DataContract(DataType.Record(listOf(DataField(FieldId("message"), text))))
        return LiteralDataValues.lift(recordOf("message" to message), contract)
    }

    private fun fields(value: DataValue): List<FieldId> =
        (value.contract.structural as DataType.Record).fields.map { it.id }
}
