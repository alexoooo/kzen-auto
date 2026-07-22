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
 * The filter stage as a Job Worker — the dataflow's predicate stage. Rather than a hardcoded column/value
 * comparison, [where] is an arbitrary Kotlin BOOLEAN EXPRESSION evaluated against each message's flat part,
 * with the columns referenced by name (`City eq "Lviv"`, `temp.number > 30`, …). It is compiled by the
 * genuine [CalculatedColumnEval] engine — the SAME engine [FormulaWorker] uses, injected as a `@Service`
 * — so the expression is type-checked against the flat part's schema ([HeaderListing]). A payload-lane
 * message auto-flattens ([JobMessage.flatView]: a scalar filters via the `value` column), so the predicate
 * works over any stream; expressions over the typed payload itself are the element-model plan's phase 3.
 *
 * The predicate is compiled lazily and recompiled only when the incoming header changes; a message is kept
 * when the compiled expression's result is truthy ([tech.kzen.auto.server.objects.report.exec.calc.ColumnValue.truthy]
 * — coercing a Boolean / numeric / "yes"/"true" result to a predicate). An empty [where] keeps every message
 * (the batch passes through untouched). The RECEIVED message is forwarded (payload intact); an all-dropped
 * batch is skipped.
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
    TransformWorker(input, output, selfLocation)
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val passThrough = where.isBlank()

    // Compiled lazily; recompiled only when the incoming header changes (HeaderListing value-compare).
    private var compiledForHeader: HeaderListing? = null
    private var compiled: CalculatedColumn<Any>? = null

    private var seen = 0L
    private var kept = 0L


    override suspend fun onElement(element: JobMessage, emit: Emitter, control: JobControl) {
        seen += 1

        if (passThrough) {
            kept += 1
            emit.send(element)
            return
        }

        val flat = element.flatView()
        val header = flat.header
        if (header != compiledForHeader) {
            compiled = control.runBlockingIo {
                calculatedColumnEval.create(
                    "filter", where, header, ClassNames.kotlinAny, classLoader)
            }
            compiledForHeader = header
        }

        if (compiled!!.evaluate(Unit, flat.record, header).truthy) {
            kept += 1
            emit.send(element)
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("seen" to seen, "kept" to kept)
}
