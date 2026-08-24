package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterType
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderIndex
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * The VALUE-SET filter stage as a Job Worker — the distinct-value whitelist predicate (Report's
 * `FilterItemController` / [ColumnFilterType], NOT the Kotlin-expression predicate that [FilterWorker] runs).
 * Each column with a non-empty value set is tested against that set: [ColumnFilterType.RequireAny] keeps a
 * record only when the field is one of the whitelisted values, [ColumnFilterType.ExcludeAll] drops it when the
 * field is one of them. A record survives only if EVERY configured column passes; surviving records are
 * forwarded downstream unchanged, rejected ones are dropped.
 *
 * The predicate is the same one Report runs: this reuses
 * [tech.kzen.auto.server.objects.report.exec.stages.ReportFilterStage]'s exact test — [RecordHeaderIndex] to map
 * the filtered columns onto the record's positions, a standalone [FlatFileRecordField] set per column for
 * allocation-free membership, and [ColumnFilterType.reject] for the accept/reject decision — so a Job chain
 * `reader → ValueSetFilter → …` produces byte-identical survivors to Report's filter over the same data (the
 * P4c A/B parity gate). The only adaptation is that the schema is discovered IN-BAND from each message's flat
 * part ([JobMessage.flatView] — a payload-lane message auto-flattens) rather than from a static
 * `inputAndFormulaColumns`: the compiled column set / types / value sets are rebuilt whenever the incoming
 * [HeaderListing] changes (like [FilterWorker] recompiles its expression), and a filter on a column absent
 * from the current header is IGNORED (exactly as `ReportFilterStage` drops filter columns that aren't in the
 * report schema). An all-empty [filter] keeps every record. The RECEIVED message is forwarded (payload intact).
 *
 * A plain [TransformWorker] (no `serve`, no scratch dir): its editor discovers candidate distinct values from an
 * upstream [SummaryWorker]'s serve port at config time (P4i), not from this Worker at run time.
 */
@Reflect
class ValueSetFilterWorker(
    input: ChannelInput<Any?>,
    output: ChannelOutput<Any?>,

    private val filter: FilterSpec,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    // The non-empty filter columns (those with a value set to match against), keyed for a fast header lookup.
    private val nonEmptyColumns: Map<HeaderLabel, ColumnFilter> = filter
        .columns
        .filterValues { it.values.isNotEmpty() }
        .mapValues { (_, spec) ->
            ColumnFilter(
                spec.type,
                spec.values.map { FlatFileRecordField.standalone(it) }.toSet())
        }

    // Compiled lazily against the incoming header; recompiled only when the header changes (value-compare).
    // Empty when no configured column is present in the current header (then every record passes).
    private var compiledForHeader: HeaderListing? = null
    private var recordHeaderIndex = RecordHeaderIndex(HeaderListing.empty)
    private var columnTypes: List<ColumnFilterType> = listOf()
    private var columnValues: List<Set<FlatFileRecordField>> = listOf()

    // Reused across records to read a field without allocating (mirrors ReportFilterStage).
    private val flyweight = FlatFileRecordField()

    private var seen = 0L
    private var kept = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: JobMessage, emit: Emitter, control: JobControl) {
        seen += 1

        val flat = element.flatView()
        val header = flat.header
        compileForHeader(header)

        if (test(flat.record, header)) {
            kept += 1
            emit.send(element)
        }
    }


    // Mirrors ReportFilterStage's constructor logic, but over the record's in-band header rather than a static
    // schema: the active columns are the non-empty filter columns that are present in this header, in header
    // order. A filter on a column absent from the header is ignored (as ReportFilterStage drops it).
    private fun compileForHeader(header: HeaderListing) {
        if (header == compiledForHeader) {
            return
        }

        val activeColumns = header.values.filter { it in nonEmptyColumns }

        recordHeaderIndex = RecordHeaderIndex(HeaderListing(activeColumns))
        columnTypes = activeColumns.map { nonEmptyColumns.getValue(it).type }
        columnValues = activeColumns.map { nonEmptyColumns.getValue(it).values }
        compiledForHeader = header
    }


    // Identical to ReportFilterStage.test: a record survives only if every active column accepts it.
    private fun test(record: FlatFileRecord, header: HeaderListing): Boolean {
        val columnIndices = recordHeaderIndex.indices(header)

        flyweight.selectHost(record)

        for (i in columnTypes.indices) {
            val columnType = columnTypes[i]
            val columnValueSet = columnValues[i]

            val indexInRecord = columnIndices[i]
            if (indexInRecord == -1) {
                if (columnType == ColumnFilterType.RequireAny) {
                    return false
                }
            }
            else {
                flyweight.selectField(indexInRecord)
                val present = columnValueSet.contains(flyweight)

                if (columnType.reject(present)) {
                    return false
                }
            }
        }

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("seen" to seen, "kept" to kept)


    //-----------------------------------------------------------------------------------------------------------------
    // A column's compiled criterion: the accept/reject rule and the whitelisted values as standalone fields.
    private class ColumnFilter(
        val type: ColumnFilterType,
        val values: Set<FlatFileRecordField>
    )
}
