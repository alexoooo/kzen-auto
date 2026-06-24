package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumn
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassNames


/**
 * The filter stage as a Job Worker — the strictly-typed dataflow's predicate stage. Rather than a hardcoded
 * column/value comparison, [where] is an arbitrary Kotlin BOOLEAN EXPRESSION evaluated against each record,
 * with the record's columns referenced by name (`City eq "Lviv"`, `temp.number > 30`, …). It is compiled by
 * the genuine [CalculatedColumnEval] engine — the SAME engine [FormulaWorker] uses, injected as a `@Service`
 * — so the expression is type-checked against the element's schema ([HeaderListing]). This is the
 * typed-element expression bridge: today the "type" is the in-band record schema; the engine generalizes to
 * a richer TypeMetadata / minted-class element type without changing this Worker (see [RecordBatch]).
 *
 * The predicate is compiled lazily and recompiled only when the incoming header changes; a record is kept
 * when the compiled expression's result is truthy ([tech.kzen.auto.server.objects.report.exec.calc.ColumnValue.truthy]
 * — coercing a Boolean / numeric / "yes"/"true" result to a predicate). An empty [where] keeps every record
 * (the batch passes through untouched). Surviving records are re-batched; an all-dropped batch is skipped.
 * The shared header rides through unchanged; the output closes once the input ends (close propagation).
 */
@Reflect
class FilterWorker(
    private val input: ChannelInput<Any?>,
    private val output: ChannelOutput<Any?>,

    private val where: String,
    private val selfLocation: ObjectLocation,

    @Service private val calculatedColumnEval: CalculatedColumnEval
):
    Worker
{
    override suspend fun run(control: JobControl) {
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
        val passThrough = where.isBlank()

        // Compiled lazily; recompiled only when the incoming header changes (HeaderListing value-compare).
        var compiledForHeader: HeaderListing? = null
        var compiled: CalculatedColumn<Any>? = null

        var seen = 0L
        var kept = 0L
        try {
            for (item in input) {
                control.checkpoint()
                val batch = item as RecordBatch

                if (passThrough) {
                    seen += batch.records.size
                    kept += batch.records.size
                    output.send(batch)
                    control.publishProgress(selfLocation, mapOf("seen" to seen, "kept" to kept))
                    continue
                }

                if (batch.header != compiledForHeader) {
                    compiled = control.runBlockingIo {
                        calculatedColumnEval.create(
                            "filter", where, batch.header, ClassNames.kotlinAny, classLoader)
                    }
                    compiledForHeader = batch.header
                }
                val predicate = compiled!!

                val keptRecords = ArrayList<FlatFileRecord>(batch.records.size)
                for (record in batch.records) {
                    seen += 1
                    if (predicate.evaluate(Unit, record, batch.header).truthy) {
                        keptRecords.add(record)
                        kept += 1
                    }
                }
                if (keptRecords.isNotEmpty()) {
                    output.send(RecordBatch(batch.header, keptRecords))
                }
                control.publishProgress(selfLocation, mapOf("seen" to seen, "kept" to kept))
            }
            control.publishProgress(selfLocation, mapOf("seen" to seen, "kept" to kept), force = true)
        }
        finally {
            output.close()
        }
    }
}
