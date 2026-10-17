package tech.kzen.auto.server.objects.job.value.recycle

import org.junit.Test
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.exec.engine.resource.Holder
import tech.kzen.lib.common.exec.engine.resource.ResourceOwners
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue


class RecyclableTest {
    private val pool = RecyclablePool { PooledRecord(it) }
    private val holder = Holder("test")


    @Test
    fun theLastReleaseReturnsTheObjectToItsPool() {
        val record = pool.acquire()
        val first = record.resource.hold(holder)
        val second = record.owners.hold(holder)
        first.release()
        assertEquals(0, record.generation, "one hold left")
        assertEquals(1, record.holds())
        second.release()
        assertEquals(1, record.generation)
        assertFalse(second.isActive)
        assertTrue(record.resource.isClosed, "the trip's resource is closed by the return")
        assertSame(record, pool.acquire())
        assertEquals(1, pool.created)
    }


    @Test
    fun aReusedObjectCarriesAFreshResource() {
        val record = pool.acquire()
        val spent = record.resource
        record.resource.hold(holder).release()
        assertSame(record, pool.acquire())
        assertNotSame(spent, record.resource)
        assertFalse(record.resource.isClosed)
        assertEquals(listOf(record.resource), record.owners.entries())
        assertTrue(record.fill(2, "two").owners!!.contains(record.resource), "a value filled after the reuse owns the new trip")
    }


    @Test
    fun aForfeitedObjectNeverReturnsToItsPool() {
        val record = pool.acquire()
        val hold = record.resource.hold(holder)
        record.forfeit()
        record.forfeit()
        assertTrue(hold.isActive, "forfeiting keeps the holds")
        hold.release()
        assertTrue(record.isForfeited)
        assertEquals(0, record.generation)
        assertFalse(record.resource.isClosed)
        assertNotSame(record, pool.acquire())
        assertEquals(2, pool.created)
    }


    @Test
    fun aHoldOnARecycledObjectIsRefusedByName() {
        val record = pool.acquire()
        val hold = record.resource.hold(holder)
        hold.release()
        hold.release()
        assertEquals(1, record.generation, "a second release of the same hold is a no-op")
        val failure = assertFailsWith<IllegalStateException> { record.resource.hold(holder) }
        assertTrue(failure.message!!.contains("already closed"), failure.message)
        assertFailsWith<IllegalStateException> { record.forfeit() }
    }


    @Test
    fun aReadAfterRecycleFailsByName() {
        PooledRecord.useAfterRecycle.set(0)
        val record = pool.acquire()
        val value = record.fill(7, "seven")
        record.resource.hold(holder).release()
        val failure = assertFailsWith<IllegalStateException> { value.access.state(value.root) }
        assertTrue(failure.message!!.contains("PooledRecord 7 was read after it was recycled"), failure.message)
        assertEquals(1, PooledRecord.useAfterRecycle.getAndSet(0))
    }


    @Test
    fun aValueIsFoundThroughItsAccessAndKeepsItsOwnersWhateverItsMetadata() {
        val record = pool.acquire()
        val value = record.fill(1, "one")
        val metadata = ValueMetadata.of(DataOverlay.record(null, listOf(FieldId("m") to LiteralDataValues.lift(1L))))
        val withMetadata = value.withMetadata(metadata)
        assertSame(record, (withMetadata.access as RecyclableAccess).recyclable)
        assertSame(record, (withMetadata.payload().access as RecyclableAccess).recyclable)
        assertEquals(listOf(record.resource), withMetadata.owners!!.entries())
        assertEquals(listOf(record.resource), withMetadata.payload().owners!!.entries())
        assertEquals(listOf(record.resource), value.child(value.access.field(value.root, PooledRecord.idField)).owners!!.entries())
        assertFalse(LiteralDataValues.lift("ordinary").access is RecyclableAccess)
    }


    @Test
    fun aDerivedValueInheritingTheOwnersHoldsTheObjectThroughThem() {
        val record = pool.acquire()
        val input = record.fill(1, "one")
        val derived = LiteralDataValues.lift(listOf(1L, "one")).withOwners(ResourceOwners.empty + input.owners)
        val hold = derived.owners!!.hold(holder)
        assertEquals(1, record.holds())
        hold.release()
        assertEquals(1, record.generation, "the derived value's last release recycles the object it was read from")
        assertFalse(record.isForfeited)
    }
}
