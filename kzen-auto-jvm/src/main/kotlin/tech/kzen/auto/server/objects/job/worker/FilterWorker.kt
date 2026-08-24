package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumn
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * The filter stage as a Job Worker — the dataflow's predicate stage. Rather than a hardcoded column/value
 * comparison, [where] is an arbitrary Kotlin BOOLEAN EXPRESSION evaluated against each message with the full
 * expression scope: the lane's INFERRED PAYLOAD TYPE as the receiver (a typed payload's members are bare —
 * `age > 30` — with the `payload` alias as the escape hatch; the type comes from [JobControl.payloadType],
 * the same static walk the editor displays), the flat part's columns bare by name (`City eq "Lviv"`,
 * `temp.number > 30`, …), and the Job's declared parameters bare and typed (`value > threshold`, values read
 * via [JobControl.parameter]). It is compiled by the genuine [CalculatedColumnEval] engine — the SAME engine
 * [FormulaWorker] uses, injected as a `@Service`. A payload-lane message auto-flattens
 * ([JobMessage.flatView]: a scalar filters via the `value` column), so the predicate works over any stream.
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
    private var compiled: CalculatedColumn<Any?>? = null

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
            // The full expression scope: the lane's inferred payload type as receiver, the columns bare, and
            // the Job's declared parameters bare and typed — parameter values are run-constant, injected once
            // per compiled instance (never baked into the generated source). Receiver and parameter types are
            // run-constant too, so the header is the only recompile trigger.
            val parameters = control.parameters()
            val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
            compiled = control.runBlockingIo {
                calculatedColumnEval.create(
                    "filter", where, header, receiverType, classLoader, parameters)
            }
            compiled!!.setParameters(parameters.components.map { control.parameter(it.name.value) })
            compiledForHeader = header
        }

        if (compiled!!.evaluate(element.payload, flat.record, header).truthy) {
            kept += 1
            emit.send(element)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A filter forwards the received message untouched (identity lane); its contribution to the walk is
    // static validation of [where] — a full compile where the effective column view is known (the same scope
    // the runtime compile will use, including the auto-flatten `value` column of a concrete payload lane),
    // and a syntax-only check where it is not, since malformed source cannot compile under any header.
    override fun payloadFlow(input: WorkerLane, context: WorkerLaneContext): WorkerLaneAttempt {
        if (passThrough) {
            return WorkerLaneAttempt(input, null)
        }

        val columns = input.consumerFlatColumns()
            ?: return WorkerLaneAttempt(input, calculatedColumnEval.validateSyntax(where))

        val error = calculatedColumnEval.validate(
            "filter", where, columns,
            input.payloadType ?: TypeMetadata.anyNullable, context.classLoader, context.parameters)

        return WorkerLaneAttempt(input, error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("seen" to seen, "kept" to kept)
}
