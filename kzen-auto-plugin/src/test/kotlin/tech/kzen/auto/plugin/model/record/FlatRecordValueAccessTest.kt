package tech.kzen.auto.plugin.model.record

import com.sun.management.ThreadMXBean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue
import java.lang.management.ManagementFactory


class FlatRecordValueAccessTest {
    private val id = FieldId("id")
    private val reading = FieldId("reading")
    private val header = FlatRecordHeader(DataContract(DataType.Record(listOf(
        DataField(id, DataType.Scalar(ScalarKind.Integer(64))),
        DataField(reading, DataType.Scalar(ScalarKind.Floating(64)))))))


    @Test
    fun recordIsItsOwnValueAccessAndPrimitiveReadsReuseExistingCache() {
        val record = FlatFileRecord.of("42", "12.5")
        record.attachHeader(header)
        val value = DataValue(record, DataNode(0))

        assertSame(record, value.access)
        assertEquals(42L, record.readLong(record.field(value.root, id)))
        val readingNode = record.field(value.root, reading)
        assertEquals(12.5, record.readDouble(readingNode))

        repeat(20_000) { record.readDouble(readingNode) }
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        if (!bean.isThreadAllocatedMemorySupported) return
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().threadId()
        val before = bean.getThreadAllocatedBytes(thread)
        var sum = 0.0
        repeat(100_000) { sum += record.readDouble(readingNode) }
        val allocated = bean.getThreadAllocatedBytes(thread) - before
        assertEquals(1_250_000.0, sum)
        assertTrue(allocated <= 4_096, "flat primitive-read loop allocated $allocated bytes")
    }


    @Test
    fun clearKeepsHeaderWhileCopyCloneAndExchangeMoveItWithContents() {
        val otherHeader = FlatRecordHeader(DataContract(DataType.Record(listOf(
            DataField(FieldId("other"), DataType.Scalar(ScalarKind.Text))))))

        val reusable = FlatFileRecord.of("1", "2")
        reusable.attachHeader(header)
        reusable.clear()
        reusable.add("3")
        reusable.add("4")
        assertEquals(header.contract, reusable.contract(DataNode(0)))

        val copied = FlatFileRecord()
        copied.copy(reusable)
        assertEquals(header.contract, copied.contract(DataNode(0)))

        val cloned = FlatFileRecord()
        cloned.clone(reusable)
        assertEquals(header.contract, cloned.contract(DataNode(0)))

        val other = FlatFileRecord.of("x")
        other.attachHeader(otherHeader)
        reusable.exchange(other)
        assertEquals(otherHeader.contract, reusable.contract(DataNode(0)))
        assertEquals(header.contract, other.contract(DataNode(0)))
    }


    @Test
    fun missingOrMismatchedHeaderFailsImmediately() {
        assertThrows(RuntimeException::class.java) { FlatFileRecord.of("x").contract(DataNode(0)) }
        assertThrows(RuntimeException::class.java) { FlatFileRecord.of("x").attachHeader(header) }
    }
}
