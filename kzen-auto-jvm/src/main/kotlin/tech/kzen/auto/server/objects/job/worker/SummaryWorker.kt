package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabelMap
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.exec.summary.model.ValueSummaryBuilder
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * A PASSTHROUGH analytics Worker that computes a live per-column [TableSummary] (count / numeric stats / value
 * histogram / opaque sample) over every record flowing through it — reusing Report's substrate-neutral
 * [ValueSummaryBuilder] engine (bounded memory: ≤100 histogram buckets + ≤100 sample per column, so NO scratch
 * dir) — while forwarding each record downstream UNCHANGED. Being a transform (not a terminal sink) it composes
 * into any pipeline (`reader → summary → filter → writer`), and its summary is a side-observation of the stream.
 *
 * INTERACTIVITY (via [TransformWorker]'s optional `serve` port, unified on [WorkerBase.snapshot]):
 * - **Pull:** answers on-demand [TableSummary] queries over its external duplex `serve` Channel ([onQuery]) — the
 *   source the JS value-set-filter editor reads distinct values from (a column's [NominalValueSummary] histogram
 *   keys), the Job analogue of Report's `FilterItemController` reading `tableSummary`.
 * - **Push:** publishes a running row count to its trace for the always-on live card; the final end-of-stream
 *   push adds the full [TableSummary], so the post-run card renders from the persisted trace.
 *
 * The summary columns are discovered from the FIRST record's header (all its columns); later records map onto
 * them by name via [RecordHeaderIndex] (a column absent from a given record is skipped). LIVE-EDIT MIGRATION: the
 * accumulated builders are carried forward into the rebuilt instance (they are pure in-memory state, no live
 * handle), so — like [PreviewWorker] — the summary keeps accumulating across a pause / edit-config / continue
 * rather than resetting (exact iff the upstream reader RESUMES rather than restarts).
 */
@Reflect
class SummaryWorker(
    input: ChannelInput<Any?>,
    output: ChannelOutput<Any?>,
    serve: ChannelServer<Any?, Any?>,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation, serve)
{
    //-----------------------------------------------------------------------------------------------------------------
    // Discovered from the first record's header (all subsequent records map onto it by name). One builder per
    // summarized column, index-aligned with headerIndex.columnHeaders. Confined to onElement (the work coroutine).
    private var headerIndex: RecordHeaderIndex? = null
    private var builders: List<ValueSummaryBuilder> = listOf()
    private var count = 0L

    // Reused across records to read a field without allocating (mirrors ReportSummary).
    private val flyweight = FlatFileRecordField()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: JobMessage, emit: Emitter, control: JobControl) {
        val flat = element.flatView()
        val header = flat.header
        val index = ensureInitialized(header)

        flyweight.selectHost(flat.record)
        val indices = index.indices(header)
        for (i in builders.indices) {
            val fieldIndex = indices[i]
            if (fieldIndex == -1) {
                continue
            }
            flyweight.selectField(fieldIndex)
            builders[i].add(flyweight)
        }
        count += 1

        // Passthrough: the record continues downstream unchanged (the summary is a side-observation).
        emit.send(element)
    }


    private fun ensureInitialized(header: HeaderListing): RecordHeaderIndex {
        val existing = headerIndex
        if (existing != null) {
            return existing
        }

        val created = RecordHeaderIndex(header)
        headerIndex = created
        builders = header.values.map { ValueSummaryBuilder() }
        return created
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The immutable summary shared with the serve coroutine + used for progress; built fresh after each batch.
    override fun snapshot(): TableSummary =
        currentAccumulation().tableSummary()


    // Push is a teaser, pull is the payload: periodic pushes carry only the running row count (every emit is
    // retained in engine history, so serializing the full TableSummary per publish grows it without bound);
    // the final forced push adds the full summary, so the card renders it from the persisted push after the
    // run (survives run-end + browser refresh, like Preview's teaser). During the run the full summary is
    // served on demand (onQuery) for the downstream filter / pivot editors.
    override fun progress(snapshot: Any?, force: Boolean): Map<String, Any?> {
        if (!force) {
            return mapOf(JobConventions.progressCountKey to count)
        }
        val tableSummary = snapshot as? TableSummary
            ?: TableSummary.empty
        return mapOf(
            JobConventions.progressCountKey to count,
            JobConventions.progressSummaryKey to tableSummary.toCollection())
    }


    override fun onQuery(request: Any?, snapshot: Any?): ExecutionResult {
        val tableSummary = snapshot as? TableSummary
            ?: TableSummary.empty
        return ExecutionSuccess.ofValue(ExecutionValue.of(tableSummary.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Carry the accumulated builders forward across a live edit (pure in-memory state, no live handle to detach),
    // so the summary resumes rather than resets. Captured on the outgoing instance while parked at a checkpoint.
    override fun captureMigrationState(): Accumulation =
        currentAccumulation()


    override fun loadMigrationState(captured: Any?) {
        val accumulation = captured as Accumulation
        headerIndex = accumulation.headerIndex
        builders = accumulation.builders
        count = accumulation.count
    }


    private fun currentAccumulation(): Accumulation =
        Accumulation(headerIndex, builders, count)


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The Worker's accumulated summary state — the live [builders] plus the header index and row count. Doubles
     * as the migration-carry payload and (via [tableSummary]) the source of the immutable [TableSummary] the
     * serve / progress paths read. Holds MUTABLE builders, so [tableSummary] is only safe to call from the work
     * coroutine (or once the run has settled), never concurrently with [onElement].
     */
    class Accumulation(
        val headerIndex: RecordHeaderIndex?,
        val builders: List<ValueSummaryBuilder>,
        val count: Long
    ) {
        fun tableSummary(): TableSummary {
            val index = headerIndex
                ?: return TableSummary.empty

            return TableSummary(HeaderLabelMap(
                index.columnHeaders.values.withIndex().associate { (i, label) ->
                    label to builders[i].build()
                }))
        }
    }
}
