package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.auto.plugin.api.data.Borrowed
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueAccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.platform.ClassName
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap


/**
 * One run's ownership ledger (E9 item 2): every `AutoCloseable` native the run adopted, keyed by object
 * identity, with its named leases; the owner set each in-flight value carries; and the best-effort close-all
 * that runs in the run's post-join teardown. Adoption is the only place that touches the process-wide
 * [NativeIdentityRegistry] (owned-by-another-run and use-after-close are its named errors); `lift` stays
 * run-blind. Once a native closes, the ledger drops its strong reference — a closed entry survives only as
 * the registry's weak tombstone. Owner sets are keyed by a value's [ValueAccess], which a child navigated from
 * a value shares — so navigation inherits for free — and [inherit] carries a parent's owners onto a fresh
 * non-scalar derivative (a Formula's output). Thread-safe.
 */
class RunOwnershipLedger(
    val runId: LogicRunId,
    private val registry: NativeIdentityRegistry = NativeIdentityRegistry.global
) {
    /** What adopting a candidate yielded: the native to lift (unwrapped from [Borrowed]) and the owners it carries. */
    class Adoption(
        val native: Any?,
        val owners: OwnerSet,
        /** The producer lease on a newly adopted entry; none when nothing was adopted. */
        val producerLease: ValueLease
    )


    private val lock = Any()
    private val entries = IdentityHashMap<Any, OwnedNative>()
    private var closedCount = 0
    private var adoptedCount = 0
    private val ownersByAccess = WeakHashMap<ValueAccess, OwnerSet>()

    // Fast paths for the unowned common case (E9 must not tax ordinary scalars): whether any value ever carried
    // owners, and per native root class whether it is closeable at all — a scalar's boxed class is looked at once.
    @Volatile
    private var anyOwners = false
    private val closeableByClass = ConcurrentHashMap<ClassName, Boolean>()


    /**
     * Adopts [candidate] on behalf of [holder] when it is an `AutoCloseable` the run does not own yet; a
     * [Borrowed] is unwrapped and never adopted; anything else passes through. The returned owners are the new
     * entry followed by [inherited], so a closeable child of an owned parent depends on both and, released
     * together, closes before its parent.
     */
    fun adopt(candidate: Any?, holder: LeaseHolder, inherited: OwnerSet = OwnerSet.empty): Adoption {
        val native = if (candidate is Borrowed<*>) candidate.value else candidate
        if (native !is AutoCloseable) {
            return Adoption(native, inherited, ValueLease.none)
        }
        if (candidate is Borrowed<*>) {
            // Declared host-managed, for this and every later boundary (a send of the lifted value included)
            registry.markBorrowed(native)
            return Adoption(native, inherited, ValueLease.none)
        }
        val entry = synchronized(lock) {
            entries[native]?.let { existing ->
                throw IllegalStateException(
                    "Native ${native.javaClass.name} is already owned by this run (holds ${existing.holds()})")
            }
            if (!registry.adopt(native, runId)) {
                return Adoption(native, inherited, ValueLease.none)
            }
            adoptedCount++
            OwnedNative(native, ::onClosed).also { entries[native] = it }
        }
        val lease = entry.lease(holder)
        return Adoption(native, OwnerSet.of(entry) + inherited, lease)
    }


    /**
     * A transfer boundary's hold (E9 item 2) — the channel's at a send, the producer's at a reader's pull:
     * [holder]'s hold on everything [value] depends on. A root native that is an `AutoCloseable` the run does
     * not own yet (a Worker-created closeable, a pulled item) is adopted here — unless it is Borrowed — and the
     * value's owners become that entry plus whatever it already carried. Null when the value depends on
     * nothing owned, so an unowned transfer costs no lease.
     */
    fun hold(value: DataValue, holder: LeaseHolder): ValueLease? {
        val inherited = owners(value)
        val native = closeableRoot(value)
        if (native != null && entryOf(native) == null) {
            val adoption = adopt(native, holder, inherited)
            if (adoption.producerLease.isActive) {
                attach(value, adoption.owners)
                if (inherited.isEmpty) {
                    return adoption.producerLease
                }
                return CompositeLease(listOf(adoption.producerLease, inherited.lease(holder)))
            }
        }
        if (inherited.isEmpty) {
            return null
        }
        return inherited.lease(holder)
    }


    // The value's root native when it is an AutoCloseable; null otherwise, decided per native class after the
    // first sight of that class so a stream of scalars never reads its boxed natives
    private fun closeableRoot(value: DataValue): AutoCloseable? {
        // A scalar's native is a boxed primitive, a string, a decimal or bytes: never closeable, and the common
        // case, decided by one type check
        if (value.contract.structural is DataType.Scalar) {
            return null
        }
        val rootNative = value.contract.nativeByPath[DataTypePath.root]
            ?: return null
        if (closeableByClass[rootNative.className] == false) {
            return null
        }
        val native = JobDataValues.native(value)
        val closeable = native is AutoCloseable
        closeableByClass.putIfAbsent(rootNative.className, closeable)
        return native as? AutoCloseable
    }


    /**
     * Owner propagation for a derived value (E9 item 3): a non-scalar [child] computed from [parent] — an
     * expression may have returned anything reachable from its input — inherits the parent's owners, so the
     * parent stays open while the child is in flight. A scalar never inherits.
     */
    fun inherit(child: DataValue, parent: DataValue) {
        if (child === parent || child.contract.structural is DataType.Scalar) {
            return
        }
        val parentOwners = owners(parent)
        if (parentOwners.isEmpty) {
            return
        }
        attach(child, owners(child) + parentOwners)
    }


    /** The ledger entry for an owned native, or null when the run does not own it. */
    fun entryOf(native: Any): OwnedNative? = synchronized(lock) { entries[native] }


    /** Records the owners a lifted value carries; children navigated from it share the access and so the owners. */
    fun attach(value: DataValue, owners: OwnerSet) {
        if (owners.isEmpty) return
        anyOwners = true
        synchronized(lock) { ownersByAccess[value.access] = owners }
    }


    /** The owners a value carries; empty for an unowned value. */
    fun owners(value: DataValue): OwnerSet {
        if (!anyOwners) {
            return OwnerSet.empty
        }
        return synchronized(lock) { ownersByAccess[value.access] } ?: OwnerSet.empty
    }


    /** A named hold on everything [value] depends on; a no-op lease for an unowned value. */
    fun retain(value: DataValue, holder: LeaseHolder): ValueLease = owners(value).lease(holder)


    /** Live entries (not yet closed). */
    fun live(): List<OwnedNative> = synchronized(lock) { entries.values.toList() }


    /** Aggregated live holds by holder, for the run's progress publication (E9 item 5). */
    fun holdsByHolder(): Map<LeaseHolder, Int> {
        val counts = mutableMapOf<LeaseHolder, Int>()
        for (entry in live()) {
            for ((holder, count) in entry.holds()) {
                counts[holder] = (counts[holder] ?: 0) + count
            }
        }
        return counts
    }


    /**
     * Teardown after every Worker has joined: releases every outstanding hold and closes every live entry,
     * attempting all of them. With a [primary] processing failure the close failures attach to it as suppressed
     * and null is returned (the caller is already unwinding with the primary); with none, the first close
     * failure is returned with the rest suppressed, for the caller to throw.
     */
    fun closeAll(primary: Throwable?): Throwable? {
        val failures = mutableListOf<Throwable>()
        for (entry in live()) {
            entry.forceClose()?.let { failures += it }
        }
        if (failures.isEmpty()) {
            return null
        }
        if (primary != null) {
            failures.forEach { primary.addSuppressed(it) }
            return null
        }
        val first = failures.first()
        failures.drop(1).forEach { first.addSuppressed(it) }
        return first
    }


    // The strong reference leaves with the close: only the registry's weak tombstone remembers the identity
    private fun onClosed(entry: OwnedNative) {
        synchronized(lock) {
            entries.remove(entry.native)
            closedCount++
        }
        registry.markClosed(entry.native)
    }


    /** How many entries this run has closed, for diagnostics. */
    fun closedCount(): Int = synchronized(lock) { closedCount }


    /** Live (adopted, not yet closed) entries. */
    fun liveCount(): Int = synchronized(lock) { entries.size }


    /** Adoptions plus closes: a clock that advances whenever the run's ownership makes progress (E9 item 5). */
    fun activityCount(): Long = synchronized(lock) { (adoptedCount + closedCount).toLong() }


    /** [holder]'s live holds across every entry. */
    fun holdsOf(holder: LeaseHolder): Int = holdsByHolder()[holder] ?: 0


    /** A bounded description of the live natives (class and holds), for a warning — never the objects. */
    fun describeLive(limit: Int): List<String> =
        live().take(limit).map { entry -> "${entry.native.javaClass.simpleName}(${entry.holds()})" }
}
