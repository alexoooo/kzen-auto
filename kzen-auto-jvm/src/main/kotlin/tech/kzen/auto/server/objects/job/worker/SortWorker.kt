package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.sort.SortSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.auto.server.exec.job.ownership.ValueLeases
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * The SORT stage as a Job Worker: it BUFFERS every incoming record and, at end-of-stream ([onComplete]), emits
 * them re-ordered by a multi-key [SortSpec] (first key primary, ties broken by the next). Being a transform it
 * composes into any pipeline (`reader → sort → writer` / `→ preview`).
 *
 * COMPARISON is a provable TOTAL ORDER (so the stable sort never trips a comparator-contract violation on
 * adversarial data): per key, if BOTH fields parse as numbers they compare numerically; a numeric field sorts
 * BEFORE a non-numeric one; two non-numeric fields compare lexically. Numbers are read via the same
 * [FlatFileRecordField.toDoubleOrNan] the pivot / summary engines use, so `"1"` and `"1.0"` are one sort value.
 * The sort is STABLE (`sortedWith`), so equal keys keep arrival order — deterministic output. Each record
 * resolves its key columns against its OWN header (a column absent from a record sorts as an empty/non-numeric
 * field), so a mid-stream schema change is tolerated.
 *
 * IN-MEMORY v1: the whole stream is held in [buffer], and [onComplete] emits it as one flushed batch. Bounded
 * only by heap; a disk-spill external merge sort (and a streamed, back-pressured drain) is the documented
 * follow-up — this Worker is the in-memory baseline the plan calls for. It has NO `serve` port and opens NO
 * scratch dir.
 *
 * OWNERSHIP (E9): a buffered element the run owns is kept beyond its callback, so the Sort takes an explicit
 * [JobControl.retain] lease per such element — the ledger names this Sort as the holder — and releases them
 * only after [onComplete] has sent the sorted stream on (each send took the output channel's hold first). The
 * leases migrate with the buffer; a Sort the edit removed drops its buffer and its holds with it.
 *
 * LIVE-EDIT MIGRATION: the accumulated [buffer] is carried forward into the rebuilt instance
 * ([captureMigrationState] / [loadMigrationState]) — like [SummaryWorker]'s builders, it is pure in-memory data
 * with no live handle. This is REQUIRED for correctness, not just an optimization: an unchanged upstream
 * A file source RESUMES from its cursor across a pause / edit / continue, so a SortWorker that
 * restarted empty would silently drop every pre-pause row from the sort. Carryover is UNCONDITIONAL of the sort
 * spec — the buffered INPUT is valid whatever the ordering, and the rebuilt instance re-sorts it with the edited
 * spec. Capture happens while parked at the pre-receive checkpoint (buffer consistent) or, once [onComplete]
 * is draining, while a send is parked on backpressure: the drain claims each element BEFORE sending it, so the
 * carried buffer is exactly the unsent remainder (a parked element is in the channel's in-flight batch, carried
 * by the channel) — the rebuilt instance re-sorts and emits that remainder, no element twice, none lost — and
 * the buffer is empty once emitted so a post-completion rebuild re-emits nothing.
 */
@Reflect
class SortWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,

    private val sort: SortSpec,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    // The sort directions, index-aligned with sort.columns — precomputed for the hot comparator.
    private val ascending = BooleanArray(sort.columns.size) { sort.columns[it].ascending }

    // Every buffered message (whole stream); replaced with a fresh empty list once emitted in onComplete, and
    // carried across a live edit by capture/loadMigrationState.
    private var buffer = ArrayList<DataValue>()
    private var buffered = 0L
    private var emitted = 0L

    // The explicit holds on the owned elements in the buffer (E9): only owned elements cost a lease.
    private var leases = ArrayList<ValueLease>()

    // The sorted stream being drained by onComplete and how far it got: a migration cut while a send is parked
    // carries the unsent remainder (claimed before each send). @Volatile: read at the capture barrier.
    private var draining: List<Decorated>? = null
    @Volatile
    private var drained = 0

    // Sort-column field positions, cached per header (records typically share one header).
    private var indexedForHeader: HeaderListing? = null
    private var columnIndices = IntArray(0)

    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        // Accumulate only: the sorted stream is emitted once, at end-of-stream (onComplete).
        val lease = control.retain(element)
        if (lease.isActive) {
            leases.add(lease)
        }
        buffer.add(element)
        buffered += 1
    }


    override suspend fun onComplete(emit: Emitter, control: JobControl) {
        if (buffer.isEmpty()) {
            return
        }

        // Decorate → stable sort → emit (Schwartzian: each key's fields are read once, not per comparison).
        val sorted = buffer
            .map { decorate(it) }
            .sortedWith(comparator)

        draining = sorted
        drained = 0
        for (decorated in sorted) {
            // Claimed before the send: a send parked mid-flush holds the element in the channel, which a
            // migration carries — the rebuilt instance must not emit it again
            drained += 1
            emit.send(decorated.message)
            emitted += 1
        }
        draining = null

        // Release the buffered input; a rebuilt instance (post-completion live edit) must not re-emit it. The
        // output channel holds every owned element now, so letting go of this Worker's holds closes nothing.
        buffer = ArrayList()
        val held = leases
        leases = ArrayList()
        ValueLeases.releaseAll(held)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun decorate(message: DataValue): Decorated {
        val projection = JobDataValues.projection(message)
        val indices = indicesFor(projection.header)

        val texts = Array(ascending.size) { "" }
        val numbers = DoubleArray(ascending.size) { Double.NaN }

        for (i in ascending.indices) {
            val fieldIndex = indices[i]
            if (fieldIndex in 0 until projection.size) {
                texts[i] = projection.render(fieldIndex)
                numbers[i] = texts[i].toDoubleOrNull()?.takeIf(Double::isFinite) ?: Double.NaN
            }
        }

        return Decorated(message, texts, numbers)
    }


    // Each sort column's field index within the given header (-1 if absent), cached for the common shared-header
    // case. HeaderLabel is value-compared, so indexOf resolves the column regardless of record identity.
    private fun indicesFor(header: HeaderListing): IntArray {
        if (header == indexedForHeader) {
            return columnIndices
        }

        val indices = IntArray(ascending.size) { i ->
            header.values.indexOf(sort.columns[i].column)
        }
        indexedForHeader = header
        columnIndices = indices
        return indices
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val comparator = Comparator<Decorated> { a, b ->
        for (i in ascending.indices) {
            val cmp = compareField(a.numbers[i], a.texts[i], b.numbers[i], b.texts[i])
            if (cmp != 0) {
                return@Comparator if (ascending[i]) cmp else -cmp
            }
        }
        0
    }


    // A provable total order: both-numeric → numeric; numeric sorts before non-numeric; both-text → lexical.
    private fun compareField(aNumber: Double, aText: String, bNumber: Double, bText: String): Int {
        val aIsNumber = !aNumber.isNaN()
        val bIsNumber = !bNumber.isNaN()
        return when {
            aIsNumber && bIsNumber -> aNumber.compareTo(bNumber)
            aIsNumber -> -1
            bIsNumber -> 1
            else -> aText.compareTo(bText)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("buffered" to buffered, "emitted" to emitted)


    //-----------------------------------------------------------------------------------------------------------------
    override fun captureMigrationState(): Any {
        // Mid-drain: only what has not been sent yet is the rebuilt instance's to emit
        val remainder = draining?.let { sorted -> ArrayList(sorted.drop(drained).map { it.message }) }
        return BufferState(remainder ?: buffer, buffered, leases)
    }


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? BufferState
            ?: return
        buffer = state.buffer
        buffered = state.buffered
        leases = state.adoptLeases()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The carried run-scoped state: the accumulated input buffer (pure data, no live handle) + its running count
    // + the Sort's explicit holds on the owned elements in it. Closed by the engine when the edit removed the
    // Sort: the buffer is dropped, so the holds are let go.
    private class BufferState(
        val buffer: ArrayList<DataValue>,
        val buffered: Long,
        private var leases: ArrayList<ValueLease>?
    ): AutoCloseable {
        fun adoptLeases(): ArrayList<ValueLease> {
            val adopted = leases ?: ArrayList()
            leases = null
            return adopted
        }


        override fun close() {
            val dropped = leases ?: return
            leases = null
            ValueLeases.releaseAll(dropped)
        }
    }


    // A buffered message decorated with its precomputed sort key (per-column text + numeric value).
    private class Decorated(
        val message: DataValue,
        val texts: Array<String>,
        val numbers: DoubleArray
    )
}
