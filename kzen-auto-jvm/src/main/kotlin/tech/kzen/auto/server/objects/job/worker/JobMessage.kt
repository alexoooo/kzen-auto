package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.calc.ColumnValue


/**
 * The ONE element type crossing Job channels — every producer emits messages, every consumer receives them, so
 * the framework's element dispatch ([TransformWorker] / [SinkWorker]) is uniform and no Worker pairing can
 * detonate on a ClassCastException at run time. A message carries two complementary halves:
 *
 * - **[payload]** — the strongly typed domain value (a scalar from a parameter / [FormulaSourceWorker], a child
 *   Logic's result via [RunWorker], any object). Null on the pure-flat lane (the CSV readers).
 * - **[flat]** — the columnar [FlatView] the Report-lineage Workers are built on: the schema-shared
 *   [HeaderListing] paired with one [FlatFileRecord] of values. Null on pure-payload lanes, so they pay no
 *   array allocations; the CSV lane populates it and leaves [payload] null.
 *
 * Batching is a general Channel-framework concern (elements are transparently batched for cross-Worker transfer
 * — see [tech.kzen.auto.server.objects.job.channel.JobChannel]), so the domain element is a single message and
 * the framework stays agnostic of records / schemas (it just carries `Any?`).
 *
 * OWNERSHIP TRANSFER: each message is ownership-transferred through the channel, so a sender never touches it
 * after emitting — race-free without copying. A Worker that RECEIVED a message OWNS it and MAY mutate it in
 * place before forwarding (e.g. [FormulaWorker] appends calculated fields to the flat part) — which is also
 * what makes [flatView]'s in-place materialization legal.
 *
 * A message never crosses a Logic boundary: [boundaryValue] is the outbound rule ([ResultSinkWorker] yield,
 * [RunWorker] child argument), and boundary workers wrap inbound values via [ofPayload].
 */
class JobMessage(
    var payload: Any?,
    var flat: FlatView?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        /**
         * Shared header of the single synthetic `value` column a non-Map payload flattens to — one constant
         * reference for every scalar-lane message, so [flatView] allocates no header.
         */
        val valueHeader = HeaderListing.ofUnique(listOf("value"))


        /** A pure-payload message (no flat part — [flatView] materializes one on demand). */
        fun ofPayload(payload: Any?): JobMessage =
            JobMessage(payload, null)


        /** A pure-flat message (the CSV lane): [header] is the schema-shared reference, [record] the row. */
        fun ofFlat(header: HeaderListing, record: FlatFileRecord): JobMessage =
            JobMessage(null, FlatView(header, record))
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The flat part, materialized IN PLACE from [payload] when absent — the auto-flatten fallback that keeps
     * palette-insert-and-it-works: a column-consuming Worker (writer / filter / preview) fed a payload lane
     * still sees columns. A `Map` payload flattens to keyed columns (key/value text via [ColumnValue.toText] —
     * Report's canonical scalar-to-text, so `13.0` renders `13`); anything else becomes the single shared
     * [valueHeader] `value` column. At most once per message (cached on [flat]), legal under receiver
     * ownership; a forwarded materialized view carries equivalent information.
     */
    fun flatView(): FlatView {
        flat?.let { return it }

        val materialized =
            when (val value = payload) {
                is Map<*, *> ->
                    FlatView(
                        HeaderListing.of(value.keys.map { ColumnValue.toText(it) }),
                        FlatFileRecord.of(value.values.map { ColumnValue.toText(it) }))

                else ->
                    FlatView(
                        valueHeader,
                        FlatFileRecord.of(listOf(ColumnValue.toText(value))))
            }

        flat = materialized
        return materialized
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The value this message contributes across a Logic boundary (a [ResultSinkWorker] yield, a [RunWorker]
     * child argument): the [payload] wins when present; a flat-only message materializes to an ordered
     * `Map<String, String>` (column → text, header order — `render()` disambiguates duplicate-occurrence
     * columns); an empty message is null.
     */
    fun boundaryValue(): Any? {
        payload?.let { return it }

        val flat = flat
            ?: return null

        val header = flat.header
        val record = flat.record
        val materialized = LinkedHashMap<String, String>(header.values.size)
        for (i in header.values.indices) {
            val text =
                if (i < record.fieldCount()) { record.getString(i) }
                else { "" }
            materialized[header.values[i].render()] = text
        }
        return materialized
    }
}
