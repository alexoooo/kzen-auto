package tech.kzen.auto.server.objects.job.value

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.RecordLiteral
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import java.lang.management.ManagementFactory
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue


class JobDataValuesTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val warmupReads = 20_000
    private val measuredReads = 50_000
    // The map and its keys (about 400 bytes); a failed native read costs more than that per node
    private val flatReadBudgetBytes = 1_000L

    private val text = DataType.Scalar(ScalarKind.Text)
    private val flatContract = DataContract(DataType.Record(listOf(
        DataField(FieldId("id"), text),
        DataField(FieldId("name"), text),
        DataField(FieldId("note"), text.copy(nullable = true)))))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun decimalBoundariesRemainExact() {
        val text = "12345678901234567890.1234567890123456789"
        val value = JobDataValues.lift(
            text,
            DataContract(DataType.Scalar(ScalarKind.Decimal)))

        assertEquals(BigDecimal(text), JobDataValues.native(value))
        assertEquals(BigDecimal(text), JobDataValues.boundary(value))
    }


    @Test
    fun aFlatRecordWithoutANativeMaterializesItsFields() {
        val value = JobDataValues.projectedRecord(
            flatContract,
            FlatFileRecord.of(listOf("1", "a", "")),
            listOf(DataState.Present, DataState.Present, DataState.Null))

        assertEquals(mapOf("id" to "1", "name" to "a", "note" to null), JobDataValues.native(value))
        assertEquals(mapOf("id" to "1", "name" to "a", "note" to null), JobDataValues.boundary(value))
    }


    @Test
    fun aFlatRecordWithANativeRootHandsOutTheNative() {
        val native = Any()
        val value = JobDataValues.nativeRecord(
            HeaderListing.ofUnique(listOf("id")),
            FlatFileRecord.of(listOf("1")),
            native,
            TypeMetadata(ClassName(Any::class.java.name), listOf(), false))

        assertSame(native, JobDataValues.native(value))
    }


    @Test
    fun aLiftedObjectIsHandedOutByIdentity() {
        val native = Bean("a", listOf(1, 2))
        assertSame(native, JobDataValues.boundary(JobDataValues.lift(native)))
    }


    @Test
    fun aLiteralRecordMaterializesItsFields() {
        val contract = DataContract(DataType.Record(listOf(
            DataField(FieldId("count"), DataType.Scalar(ScalarKind.Integer(32))),
            DataField(FieldId("tags"), DataType.Listing(text)))))
        val value = LiteralDataValues.lift(RecordLiteral.of(mapOf("count" to 7L, "tags" to listOf("x"))), contract)

        assertEquals(mapOf("count" to 7, "tags" to listOf("x")), JobDataValues.boundary(value))
    }


    /** A row with no native (Parse's) is read field by field, without a failed native read per node. */
    @Test
    fun aFlatRecordWithoutANativeIsReadWithoutFailing() {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        if (!threads.isThreadAllocatedMemoryEnabled) {
            threads.isThreadAllocatedMemoryEnabled = true
        }
        val value = JobDataValues.projectedRecord(
            flatContract,
            FlatFileRecord.of(listOf("1", "a", "b")),
            listOf(DataState.Present, DataState.Present, DataState.Present))

        var sink = 0
        repeat(warmupReads) {
            sink += (JobDataValues.native(value) as Map<*, *>).size
        }
        val before = threads.currentThreadAllocatedBytes
        repeat(measuredReads) {
            sink += (JobDataValues.native(value) as Map<*, *>).size
        }
        val perRead = (threads.currentThreadAllocatedBytes - before) / measuredReads

        println("WR9 flat record read: $perRead bytes per read ($sink)")
        assertTrue(perRead < flatReadBudgetBytes, "$perRead bytes per read")
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Bean(val name: String, val values: List<Int>)
}
