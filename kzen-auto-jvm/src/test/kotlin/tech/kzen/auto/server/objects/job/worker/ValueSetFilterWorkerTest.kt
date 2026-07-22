package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterType
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals


/**
 * Unit test for [ValueSetFilterWorker] in isolation: drives the transform's [ValueSetFilterWorker.run] lifecycle
 * over a fake [ChannelInput] of flat-part [JobMessage]s and a capturing [ChannelOutput], asserting which records SURVIVE
 * the distinct-value whitelist and that survivors are forwarded unchanged.
 *
 * The predicate is a verbatim copy of
 * [tech.kzen.auto.server.objects.report.exec.stages.ReportFilterStage]'s `test` (same [ColumnFilterType] accept /
 * reject rule, same [tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderIndex] mapping),
 * so these cases pin the A/B-parity behaviour at the predicate level: RequireAny whitelist, ExcludeAll blacklist,
 * multi-column AND, and — the one adaptation from Report — a filter on a column ABSENT from the in-band header is
 * IGNORED (as `ReportFilterStage` drops filter columns not in its static schema), plus a mid-stream header change
 * recompiles the active column set. The end-to-end byte-identical Report-vs-Job comparison is the P4j gate.
 */
class ValueSetFilterWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    // HeaderListing.of assigns occurrence 0 to a column's first appearance, so a filter key matches by (text, 0).
    private val header = HeaderListing.of(listOf("city", "age"))

    private fun label(text: String): HeaderLabel =
        HeaderLabel(text, 0)

    private fun record(vararg fields: String): JobMessage =
        JobMessage.ofFlat(header, FlatFileRecord.of(fields.toList()))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun requireAnyKeepsOnlyWhitelistedValuesAndForwardsUnchanged() = runBlocking {
        val lviv = record("Lviv", "30")
        val kyiv = record("Kyiv", "40")
        val lvivAgain = record("Lviv", "50")

        val filter = FilterSpec(mapOf(
            label("city") to ColumnFilterSpec(ColumnFilterType.RequireAny, setOf("Lviv"))))

        val survivors = runFilter(filter, listOf(lviv, kyiv, lvivAgain))

        // Only the whitelisted "Lviv" rows survive, and they are the SAME instances (forwarded unchanged, in order).
        assertEquals(listOf(lviv, lvivAgain), survivors)
    }


    @Test
    fun excludeAllDropsBlacklistedValues() = runBlocking {
        val lviv = record("Lviv", "30")
        val kyiv = record("Kyiv", "40")

        val filter = FilterSpec(mapOf(
            label("city") to ColumnFilterSpec(ColumnFilterType.ExcludeAll, setOf("Lviv"))))

        assertEquals(listOf(kyiv), runFilter(filter, listOf(lviv, kyiv)))
    }


    @Test
    fun multipleColumnsAreAndedTogether() = runBlocking {
        val lviv30 = record("Lviv", "30")
        val lviv40 = record("Lviv", "40")
        val kyiv30 = record("Kyiv", "30")

        // Keep city == Lviv AND age == 30: only the first record satisfies both.
        val filter = FilterSpec(mapOf(
            label("city") to ColumnFilterSpec(ColumnFilterType.RequireAny, setOf("Lviv")),
            label("age") to ColumnFilterSpec(ColumnFilterType.RequireAny, setOf("30"))))

        assertEquals(listOf(lviv30), runFilter(filter, listOf(lviv30, lviv40, kyiv30)))
    }


    @Test
    fun filterOnColumnAbsentFromHeaderIsIgnored() = runBlocking {
        val lviv = record("Lviv", "30")
        val kyiv = record("Kyiv", "40")

        // "country" is not a column in the record header: like ReportFilterStage, that filter column is ignored
        // (NOT treated as an always-absent RequireAny that rejects everything) — so every record is kept.
        val filter = FilterSpec(mapOf(
            label("country") to ColumnFilterSpec(ColumnFilterType.RequireAny, setOf("Ukraine"))))

        assertEquals(listOf(lviv, kyiv), runFilter(filter, listOf(lviv, kyiv)))
    }


    @Test
    fun emptyFilterKeepsEveryRecord() = runBlocking {
        val lviv = record("Lviv", "30")
        val kyiv = record("Kyiv", "40")

        // No columns at all, and a column whose value set is empty: both mean "no constraint", so nothing is dropped.
        val noColumns = FilterSpec(mapOf())
        assertEquals(listOf(lviv, kyiv), runFilter(noColumns, listOf(lviv, kyiv)))

        val emptyValueSet = FilterSpec(mapOf(
            label("city") to ColumnFilterSpec(ColumnFilterType.RequireAny, setOf())))
        assertEquals(listOf(lviv, kyiv), runFilter(emptyValueSet, listOf(lviv, kyiv)))
    }


    @Test
    fun recompilesActiveColumnsWhenHeaderChangesMidStream() = runBlocking {
        // First header has the filtered "city" column; the second header does NOT — so the same filter that drops
        // Kyiv under the first schema becomes a no-op (ignored absent column) under the second.
        val otherHeader = HeaderListing.of(listOf("name", "age"))

        val lviv = JobMessage.ofFlat(header, FlatFileRecord.of(listOf("Lviv", "30")))
        val kyiv = JobMessage.ofFlat(header, FlatFileRecord.of(listOf("Kyiv", "40")))
        val personA = JobMessage.ofFlat(otherHeader, FlatFileRecord.of(listOf("Ada", "30")))
        val personB = JobMessage.ofFlat(otherHeader, FlatFileRecord.of(listOf("Bob", "40")))

        val filter = FilterSpec(mapOf(
            label("city") to ColumnFilterSpec(ColumnFilterType.RequireAny, setOf("Lviv"))))

        // Kyiv dropped (city not Lviv); once the header no longer has "city", the filter is ignored and both pass.
        assertEquals(
            listOf(lviv, personA, personB),
            runFilter(filter, listOf(lviv, kyiv, personA, personB)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runFilter(filter: FilterSpec, records: List<JobMessage>): List<Any?> {
        val forwarded = mutableListOf<Any?>()
        val output = object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                forwarded.add(element)
            }
            override suspend fun flush() {}
            override fun batchSize(): Int = 1024
            override fun close() {}
        }

        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/value-set-filter-unit-test.yaml"),
            ObjectPath.parse("main.workers/filter"))

        val worker = ValueSetFilterWorker(chunkedInput(records), output, filter, selfLocation)
        worker.run(NoOpJobControl)

        return forwarded
    }


    private fun chunkedInput(records: List<JobMessage>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            // The framework TransformWorker drive loop drains whole chunks: hand it every record as one chunk, then EOF.
            private var delivered = false

            override suspend fun receiveBatch(): List<Any?>? {
                if (delivered || records.isEmpty()) {
                    return null
                }
                delivered = true
                return records
            }

            override suspend fun receive(): Any? = error("unused")

            override fun iterator(): ChannelInputIterator<Any?> = error("unused")
        }


    //-----------------------------------------------------------------------------------------------------------------
    // A ValueSetFilterWorker only consumes + emits + checkpoints + publishes; none need coordination in isolation.
    private object NoOpJobControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A ValueSetFilterWorker needs no scratch dir")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A ValueSetFilterWorker hosts no child")
    }
}
