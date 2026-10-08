package tech.kzen.auto.server.objects.job.value.recycle

import org.junit.Test
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


class RecyclableTest {
    private val pool = RecyclablePool { PooledRecord(it) }


    @Test
    fun theLastReleaseReturnsTheObjectToItsPool() {
        val record = pool.acquire()
        record.hold()
        record.hold()
        record.release()
        assertEquals(0, record.generation, "one hold left")
        record.token.release()
        assertEquals(1, record.generation)
        assertFalse(record.token.isActive)
        assertSame(record, pool.acquire())
        assertEquals(1, pool.created)
    }


    @Test
    fun aForfeitedObjectNeverReturnsToItsPool() {
        val record = pool.acquire()
        record.hold()
        record.forfeit()
        record.forfeit()
        assertTrue(record.token.isActive, "forfeiting keeps the holds")
        record.release()
        assertTrue(record.isForfeited)
        assertEquals(0, record.generation)
        assertNotSame(record, pool.acquire())
        assertEquals(2, pool.created)
    }


    @Test
    fun releasingMoreThanHeldThrowsByName() {
        val record = pool.acquire()
        record.hold()
        record.token.release()
        val failure = assertFailsWith<IllegalStateException> { record.token.release() }
        assertTrue(failure.message!!.contains(PooledRecord::class.java.name), failure.message)

        val forfeited = PooledRecord(pool)
        forfeited.hold()
        forfeited.forfeit()
        forfeited.release()
        assertFailsWith<IllegalStateException> { forfeited.release() }
    }


    @Test
    fun aReadAfterRecycleFailsByName() {
        PooledRecord.useAfterRecycle.set(0)
        val record = pool.acquire()
        val value = record.fill(7, "seven")
        record.hold()
        record.release()
        val failure = assertFailsWith<IllegalStateException> { value.access.state(value.root) }
        assertTrue(failure.message!!.contains("PooledRecord 7 was read after it was recycled"), failure.message)
        assertEquals(1, PooledRecord.useAfterRecycle.getAndSet(0))
    }


    @Test
    fun aValueIsFoundThroughItsAccessWhateverItsMetadata() {
        val record = pool.acquire()
        val value = record.fill(1, "one")
        val metadata = ValueMetadata.of(DataOverlay.record(null, listOf(FieldId("m") to LiteralDataValues.lift(1L))))
        assertSame(record, Recyclable.of(value.withMetadata(metadata)))
        assertSame(record, Recyclable.of(value.withMetadata(metadata).payload()))
        assertNull(Recyclable.of(LiteralDataValues.lift("ordinary")))
    }


    @Test
    fun onlyAnOutputOfItsOwnThatIsNotAScalarForfeitsItsInput() {
        val record = pool.acquire()
        val input = record.fill(1, "one")
        Recyclable.forfeitDerived(input.withMetadata(null), input)
        Recyclable.forfeitDerived(LiteralDataValues.lift("one"), input)
        assertFalse(record.isForfeited, "a shared access or a scalar keeps the record pooled")
        Recyclable.forfeitDerived(LiteralDataValues.lift(listOf(1L, "one")), input)
        assertTrue(record.isForfeited)
    }
}
