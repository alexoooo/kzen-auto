package tech.kzen.auto.server.objects.job.worker.content

import org.junit.Test
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.value.recycle.Recyclable
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import java.io.ByteArrayOutputStream
import kotlin.reflect.typeOf
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue


/** A pooled chunk of [Bytes] is an opaque native, handed out by identity and found by [Recyclable.of]. */
class PooledBytesTest {
    @Test
    fun bytesDescribeAsAnOpaqueNative() {
        val contract = JobDataValues.describe(typeOf<Bytes>())

        assertIs<DataType.Opaque>(contract.structural)
        assertEquals(Bytes::class.qualifiedName, contract.nativeByPath[DataTypePath.root]?.className?.asString())
        assertEquals(contract, PooledBytes.contract)
    }


    @Test
    fun aChunkIsHandedOutByIdentityAndStaysPooledInsideACallback() {
        val slot = RecyclablePool { PooledBytes(it) }.acquire()
        val buffer = slot.bytes.buffer()
        buffer.ensureByteCapacity(3)
        "abc".toByteArray().copyInto(buffer.bytes)
        buffer.bytesLength = 3
        val value = slot.value(null)

        assertSame(slot, Recyclable.of(value))
        assertSame(slot.bytes, JobDataValues.native(value))
        assertSame(slot.bytes, JobDataValues.callbackObject(value))
        assertFalse(slot.isForfeited)
        val copy = ByteArrayOutputStream()
        slot.bytes.writeTo(copy)
        assertContentEquals("abc".toByteArray(), copy.toByteArray())
        assertEquals(3, slot.bytes.length())

        assertSame(slot.bytes, JobDataValues.boundary(value))
        assertTrue(slot.isForfeited)
    }


    @Test
    fun aSlotKeepsItsValueWhileTheMetadataRepeats() {
        val slot = RecyclablePool { PooledBytes(it) }.acquire()
        val metadata = ValueMetadata.of(DataOverlay.record(null, listOf()))

        val first = slot.value(metadata)

        assertSame(first, slot.value(metadata))
        assertSame(metadata, slot.value(metadata).metadata)
        assertEquals(null, slot.value(null).metadata)
    }
}
