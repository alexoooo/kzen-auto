package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.NativeLivenessGuard
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


/**
 * Process-wide record of which run owns each adopted native identity and which identities have been closed —
 * the enforcement behind "an owned native is transferred to exactly one run in its lifetime" (E9 item 2).
 * Adoption by a second run while owned fails naming the owner; adoption after close is the use-after-close
 * error; and the [guard] the value views consult makes a read of a closed native fail by name. Entries hold
 * their object through a [WeakReference] bucketed by identity hash, so a closed resource and its graph are
 * never pinned by the tombstone; cleared entries are dropped when their bucket is next touched.
 * One touch of a concurrent map per adoption, close and read. Process-lifetime, like the adapter registry
 * it guards; `lift` never consults it — only the adoption boundaries do.
 */
class NativeIdentityRegistry {
    companion object {
        val global = NativeIdentityRegistry()
    }


    sealed interface State {
        data class Owned(val runId: LogicRunId): State
        data class Closed(val runId: LogicRunId): State
        /** Declared host-managed ([tech.kzen.auto.plugin.api.data.Borrowed]): no run ever adopts or closes it. */
        data object Borrowed: State
    }


    private class Entry(
        native: Any,
        @Volatile var state: State
    ) {
        val reference = WeakReference(native)
    }


    private val buckets = ConcurrentHashMap<Int, CopyOnWriteArrayList<Entry>>()


    /** Reads through a closed native fail by name; anything not closeable is never recorded and passes at once. */
    val guard = NativeLivenessGuard { native ->
        if (native is AutoCloseable) {
            val closed = stateOf(native) as? State.Closed
            if (closed != null) {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidState,
                    "Native ${native.javaClass.name} was closed by run ${closed.runId.value} and can no longer be read"))
            }
        }
    }


    /**
     * Records [native] as owned by [runId] and returns true; false when it is [State.Borrowed] (never adopted);
     * fails naming the current owner or the earlier close.
     */
    fun adopt(native: Any, runId: LogicRunId): Boolean {
        val bucket = bucket(native)
        synchronized(bucket) {
            when (val existing = find(bucket, native)) {
                null -> bucket.add(Entry(native, State.Owned(runId)))
                else -> when (val state = existing.state) {
                    is State.Owned -> throw IllegalStateException(
                        if (state.runId == runId) "Native ${native.javaClass.name} is already owned by this run ${runId.value}"
                        else "Native ${native.javaClass.name} is owned by run ${state.runId.value}; an instance shared across runs must be Borrowed")
                    is State.Closed -> throw IllegalStateException(
                        "Native ${native.javaClass.name} was closed by run ${state.runId.value}; a resource that reopens must be a new instance")
                    State.Borrowed -> return false
                }
            }
        }
        return true
    }


    /** Declares [native] host-managed: adoption is refused from now on. A no-op for an identity already recorded. */
    fun markBorrowed(native: Any) {
        val bucket = bucket(native)
        synchronized(bucket) {
            if (find(bucket, native) == null) {
                bucket.add(Entry(native, State.Borrowed))
            }
        }
    }


    /** Marks [native] closed by its owning run; the tombstone stays until the object is collected. */
    fun markClosed(native: Any) {
        val bucket = bucket(native)
        synchronized(bucket) {
            val entry = find(bucket, native)
                ?: throw IllegalStateException("Native ${native.javaClass.name} was never adopted")
            val owned = entry.state as? State.Owned
                ?: throw IllegalStateException("Native ${native.javaClass.name} is already closed")
            entry.state = State.Closed(owned.runId)
        }
    }


    fun stateOf(native: Any): State? {
        val bucket = buckets[System.identityHashCode(native)] ?: return null
        return find(bucket, native)?.state
    }


    /** Whether an entry for [native] still exists (for tests of the weak bookkeeping). */
    fun isTracked(native: Any): Boolean = stateOf(native) != null


    /** Live entries by identity hash bucket, after dropping cleared references (for diagnostics and tests). */
    fun trackedCount(): Int {
        var count = 0
        for (bucket in buckets.values) {
            bucket.removeIf { it.reference.get() == null }
            count += bucket.size
        }
        return count
    }


    private fun bucket(native: Any): CopyOnWriteArrayList<Entry> =
        buckets.computeIfAbsent(System.identityHashCode(native)) { CopyOnWriteArrayList() }


    private fun find(bucket: CopyOnWriteArrayList<Entry>, native: Any): Entry? {
        bucket.removeIf { it.reference.get() == null }
        return bucket.firstOrNull { it.reference.get() === native }
    }
}
