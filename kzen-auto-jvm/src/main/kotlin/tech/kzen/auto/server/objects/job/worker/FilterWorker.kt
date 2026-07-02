package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
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
 * a richer TypeMetadata / minted-class element type without changing this Worker (see [DataRecord]).
 *
 * The predicate is compiled lazily and recompiled only when the incoming header changes; a record is kept
 * when the compiled expression's result is truthy ([tech.kzen.auto.server.objects.report.exec.calc.ColumnValue.truthy]
 * — coercing a Boolean / numeric / "yes"/"true" result to a predicate). An empty [where] keeps every record
 * (the batch passes through untouched). Surviving records are re-batched; an all-dropped batch is skipped.
 *
 * A [TransformWorker]: the framework owns the drain loop, per-batch checkpoint, throttled progress, and
 * end-of-stream close propagation; this Worker only maps each batch.
 */
@Reflect
class FilterWorker(
    input: ChannelInput<Any?>,
    output: ChannelOutput<Any?>,

    private val where: String,
    selfLocation: ObjectLocation,

    @Service private val calculatedColumnEval: CalculatedColumnEval
):
    TransformWorker<DataRecord, DataRecord>(input, output, selfLocation)
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val passThrough = where.isBlank()

    // Compiled lazily; recompiled only when the incoming header changes (HeaderListing value-compare).
    private var compiledForHeader: HeaderListing? = null
    private var compiled: CalculatedColumn<Any>? = null

    private var seen = 0L
    private var kept = 0L


    override suspend fun onElement(element: DataRecord, emit: Emitter<DataRecord>, control: JobControl) {
        seen += 1

        if (passThrough) {
            kept += 1
            emit.send(element)
            return
        }

        if (element.header != compiledForHeader) {
            compiled = control.runBlockingIo {
                calculatedColumnEval.create(
                    "filter", where, element.header, ClassNames.kotlinAny, classLoader)
            }
            compiledForHeader = element.header
        }

        if (compiled!!.evaluate(Unit, element.record, element.header).truthy) {
            kept += 1
            emit.send(element)
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("seen" to seen, "kept" to kept)
}
