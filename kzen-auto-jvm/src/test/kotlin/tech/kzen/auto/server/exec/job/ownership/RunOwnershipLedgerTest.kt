package tech.kzen.auto.server.exec.job.ownership

import org.junit.Test
import tech.kzen.auto.plugin.api.data.Borrowed
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * E9 primitives (HS15): identity through wrappers, competing-run adoption, closed tombstones, owner-set close
 * ordering, Borrowed suppression, concurrent final releases, cleanup-failure precedence and weak bookkeeping.
 * Route-wide behaviour (channels, Workers, streams) is HS16–HS18's, not claimed here.
 */
class RunOwnershipLedgerTest {
    private val registry = NativeIdentityRegistry()
    private val runA = LogicRunId("run-a")
    private val runB = LogicRunId("run-b")
    private val worker = LeaseHolder("main.workers/sort")
    private val channel = LeaseHolder("main.channels/items")


    @Test
    fun identityIsTheNativeObjectThroughEveryWrapper() {
        CloseCountingResource.reset()
        val ledger = RunOwnershipLedger(runA, registry)
        val resource = CloseCountingResource("r")
        val adoption = ledger.adopt(resource, LeaseHolder.producer)
        val entry = assertNotNull(ledger.entryOf(resource))
        assertSame(resource, adoption.native)
        assertTrue(entry in adoption.owners)

        // Two values lifted from the same native share the owner set; a child navigated from either shares it too
        DefaultDataAdapterRegistry(livenessGuard = registry.guard).use { adapters ->
            val first = adapters.lift(Holder(resource))
            val second = adapters.lift(Holder(resource))
            ledger.attach(first, adoption.owners)
            ledger.attach(second, adoption.owners)
            assertTrue(entry in ledger.owners(first) && entry in ledger.owners(second))

            // Adopting the same identity again in this run is refused by name
            val again = assertFailsWith<IllegalStateException> { ledger.adopt(resource, channel) }
            assertTrue(again.message!!.contains("already owned by this run"), again.message)

            // The producer lease is the only hold; a channel hold taken before it is released keeps the count off zero
            val channelLease = ledger.retain(first, channel)
            assertEquals(mapOf(LeaseHolder.producer to 1, channel to 1), entry.holds())
            adoption.producerLease.release()
            adoption.producerLease.release()
            assertFalse(resource.isClosed, "one hold remains")
            channelLease.release()
            assertTrue(resource.isClosed)
            assertEquals(1, resource.closeCount.get())
            assertNull(ledger.entryOf(resource), "a closed entry leaves the ledger")
            assertEquals(1, ledger.closedCount())

            // The tombstone: navigating to the closed resource is fine (the holder is live), reading through it
            // fails by name; a re-adoption is use-after-close
            val resourceNode = first.access.field(first.root, FieldId("resource"))
            val error = assertFailsWith<DataAccessException> { first.access.field(resourceNode, FieldId("name")) }
            assertTrue(error.problem.message.contains("closed by run run-a"), error.problem.message)
            val reuse = assertFailsWith<IllegalStateException> { RunOwnershipLedger(runB, registry).adopt(resource, channel) }
            assertTrue(reuse.message!!.contains("was closed by run run-a"), reuse.message)
            assertEquals(1, resource.closeCount.get(), "close() is never called twice")
            assertEquals(NativeIdentityRegistry.State.Closed(runA), registry.stateOf(resource))
        }
    }


    @Test
    fun ownershipIsLinearAcrossRuns() {
        val ledgerA = RunOwnershipLedger(runA, registry)
        val ledgerB = RunOwnershipLedger(runB, registry)
        val resource = CloseCountingResource("shared")
        ledgerA.adopt(resource, LeaseHolder.producer)
        val error = assertFailsWith<IllegalStateException> { ledgerB.adopt(resource, LeaseHolder.producer) }
        assertTrue(error.message!!.contains("owned by run run-a"), error.message)
        assertTrue(error.message!!.contains("Borrowed"), "the message names the remedy")
        assertEquals(NativeIdentityRegistry.State.Owned(runA), registry.stateOf(resource))

        // Borrowed is never adopted by anyone, and unwraps for lifting
        val borrowed = ledgerB.adopt(Borrowed.of(resource), LeaseHolder.producer)
        assertSame(resource, borrowed.native)
        assertTrue(borrowed.owners.isEmpty)
        assertFalse(borrowed.producerLease.isActive)
        assertNull(ledgerB.entryOf(resource))

        // A plain value passes through unowned
        val plain = ledgerB.adopt("text", LeaseHolder.producer)
        assertEquals("text", plain.native)
        assertTrue(plain.owners.isEmpty)

        assertNull(ledgerA.closeAll(null))
        assertTrue(resource.isClosed)
    }


    @Test
    fun ownerSetsKeepAParentOpenWhileAChildIsHeldAndCloseInReleaseOrder() {
        CloseCountingResource.reset()
        val ledger = RunOwnershipLedger(runA, registry)
        val parent = CloseCountingResource("parent")
        val child = CloseCountingResource("child")
        val parentAdoption = ledger.adopt(parent, LeaseHolder.producer)
        // A newly constructed closeable derived from the parent: its own entry plus the inherited owner
        val childAdoption = ledger.adopt(child, worker, inherited = parentAdoption.owners)
        assertEquals(2, childAdoption.owners.entries().size)

        // A downstream hold on the child is a hold on both; releasing the parent's producer lease closes nothing
        val downstream = childAdoption.owners.lease(channel)
        parentAdoption.producerLease.release()
        childAdoption.producerLease.release()
        assertFalse(parent.isClosed)
        assertFalse(child.isClosed)
        assertEquals(mapOf(channel to 1), ledger.entryOf(parent)!!.holds())

        downstream.release()
        assertTrue(child.isClosed && parent.isClosed)
        assertEquals(listOf("child", "parent"), CloseCountingResource.closeOrder, "the child (first member) closes before its parent")

        // A borrowed child of an owned parent keeps only the inherited owner
        val other = CloseCountingResource("other")
        val otherAdoption = ledger.adopt(other, LeaseHolder.producer)
        val borrowedChild = ledger.adopt(Borrowed.of(CloseCountingResource("cascaded")), worker, inherited = otherAdoption.owners)
        assertEquals(otherAdoption.owners.entries(), borrowedChild.owners.entries())
        otherAdoption.producerLease.release()
        assertTrue(other.isClosed)
        assertEquals(0, CloseCountingResource.closeOrder.count { it == "cascaded" }, "kzen never closes a borrowed child")

        // A lease on a set with a closed member takes nothing and fails by name
        val stale = assertFailsWith<IllegalStateException> { otherAdoption.owners.lease(channel) }
        assertTrue(stale.message!!.contains("already closed"), stale.message)
    }


    @Test
    fun concurrentFinalReleasesCloseExactlyOnce() {
        val ledger = RunOwnershipLedger(runA, registry)
        val resource = CloseCountingResource("contended")
        val adoption = ledger.adopt(resource, LeaseHolder.producer)
        val threads = 16
        val leases = (1..threads).map { adoption.owners.lease(LeaseHolder("holder-$it")) }
        adoption.producerLease.release()
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            for (lease in leases) {
                pool.execute {
                    start.await()
                    lease.release()
                    lease.release()
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
        }
        finally {
            pool.shutdownNow()
        }
        assertEquals(1, resource.closeCount.get())
        assertTrue(leases.none { it.isActive })
        assertNull(ledger.entryOf(resource))
    }


    @Test
    fun teardownClosesEverythingWithProcessingFailurePrimaryAndCloseFailuresSuppressed() {
        val ledger = RunOwnershipLedger(runA, registry)
        val fine = CloseCountingResource("fine")
        val broken = CloseCountingResource("broken", IllegalStateException("close failed"))
        val alsoBroken = CloseCountingResource("also", IllegalStateException("also failed"))
        ledger.adopt(fine, LeaseHolder.producer)
        ledger.adopt(broken, channel)
        ledger.adopt(alsoBroken, worker)
        assertEquals(mapOf(LeaseHolder.producer to 1, channel to 1, worker to 1), ledger.holdsByHolder())

        val processing = RuntimeException("worker failed")
        assertNull(ledger.closeAll(processing), "the processing failure stays primary")
        assertTrue(fine.isClosed && broken.isClosed && alsoBroken.isClosed, "every close is attempted")
        assertEquals(setOf("close failed", "also failed"), processing.suppressed.map { it.message }.toSet())
        assertTrue(ledger.live().isEmpty())
        assertEquals(3, ledger.closedCount())

        // Without a processing failure the first close failure is primary and the rest suppressed
        val second = RunOwnershipLedger(runB, registry)
        val b1 = CloseCountingResource("b1", IllegalStateException("first"))
        val b2 = CloseCountingResource("b2", IllegalStateException("second"))
        second.adopt(b1, LeaseHolder.producer)
        second.adopt(b2, LeaseHolder.producer)
        val primary = assertNotNull(second.closeAll(null))
        assertEquals(setOf("first", "second"), (listOf(primary) + primary.suppressed).map { it.message }.toSet())
        assertEquals(1, primary.suppressed.size)

        // A native whose close threw is still closed: re-adoption is use-after-close and close() is not retried
        val retry = assertFailsWith<IllegalStateException> { RunOwnershipLedger(runA, registry).adopt(b1, channel) }
        assertTrue(retry.message!!.contains("closed by run run-b"), retry.message)
        assertEquals(1, b1.closeCount.get())
    }


    @Test
    fun closedTombstonesDoNotPinTheObject() {
        val ledger = RunOwnershipLedger(runA, registry)
        var resource: CloseCountingResource? = CloseCountingResource("collectable")
        val reference = WeakReference(resource)
        ledger.adopt(resource!!, LeaseHolder.producer).producerLease.release()
        assertTrue(resource.isClosed)
        assertTrue(registry.isTracked(resource))
        resource = null

        var attempts = 0
        while (reference.get() != null && attempts < 50) {
            System.gc()
            Thread.sleep(20)
            attempts++
        }
        assertNull(reference.get(), "the closed tombstone holds the native weakly")
        assertEquals(0, registry.trackedCount(), "cleared entries are dropped on the next touch")
    }


    class Holder(val resource: CloseCountingResource)
}
