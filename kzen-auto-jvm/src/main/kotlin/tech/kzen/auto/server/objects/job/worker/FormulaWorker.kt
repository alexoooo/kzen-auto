package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.logic.ExpressionReturnTypeInference
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumn
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.exec.calc.ColumnValue
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * The calculated-column AND payload-transform stage as a Job Worker, over the shared [CalculatedColumnEval]
 * engine (the same `@Service` Filter uses), with the full expression scope: the lane's INFERRED PAYLOAD TYPE
 * as the receiver (members bare, `payload` alias; type via [JobControl.payloadType] — the same static walk
 * the editor displays), columns bare by name, and the Job's declared parameters bare and typed (run-constant
 * values via [JobControl.parameter]). Two independent lanes, both evaluated against the INCOMING message
 * (Report's rule — formulas see the original columns and the original payload; nothing chains):
 *
 * - **[formula] — flat value formulas**: appends one flat-part field per entry to every message, each an
 *   arbitrary Kotlin expression rendered to text ([ColumnValue.toText]). A payload-lane message
 *   auto-flattens first ([JobMessage.flatView]), so formulas over a scalar stream see a `value` column. The
 *   incoming message is mutated in place — the flat record grows the computed fields and the view's header
 *   reference swaps to the augmented one.
 * - **[payload] — the payload transform**: a single expression whose RAW value (no text coercion) REPLACES
 *   the message's payload (empty = pass-through). Its column scope is the flat part AS RECEIVED (captured
 *   before any auto-flatten, and it never materializes one) — so on a flat lane it can PROMOTE columns into
 *   a typed payload (`payload: Amount.number`), while on a pure-payload lane the message forwards with no
 *   flat part and downstream auto-flatten sees the NEW payload.
 *
 * Expressions are compiled lazily and recompiled only when the relevant incoming header changes (receiver
 * and parameter types are run-constant). A [TransformWorker]: the framework owns the drain loop, per-batch
 * checkpoint, throttled progress, and end-of-stream close propagation. Compilation is heavy blocking work,
 * so it runs through [JobControl.runBlockingIo] to stay visible to quiescence detection.
 */
@Reflect
class FormulaWorker(
    input: ChannelInput<Any?>,
    output: ChannelOutput<Any?>,

    private val formula: FormulaSpec,
    private val payload: String,
    private val selfLocation: ObjectLocation,

    @Service private val calculatedColumnEval: CalculatedColumnEval
):
    TransformWorker(input, output, selfLocation)
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val formulaEntries = formula.formulas.entries.toList()
    private val formulaNames = HeaderListing.ofUnique(formulaEntries.map { it.key })
    private val formulaValues = Array(formulaEntries.size) { "" }
    private val payloadTransform = payload.isNotBlank()

    // The payload expression's record stand-in for a lane with no flat part (its compiled column scope is
    // empty there, so the record is never read).
    private val emptyRecord = FlatFileRecord()

    // Compiled lazily; recompiled only when the respective incoming header changes (HeaderListing
    // value-compare). The payload expression tracks its own header — its scope is the flat part AS RECEIVED,
    // not the auto-flattened view the formulas use.
    private var compiledForHeader: HeaderListing? = null
    private var compiledColumns: List<CalculatedColumn<Any?>> = listOf()
    private var augmentedHeader: HeaderListing = HeaderListing.empty
    private var compiledPayloadForHeader: HeaderListing? = null
    private var compiledPayload: CalculatedColumn<Any?>? = null

    private var computed = 0L


    override suspend fun onElement(element: JobMessage, emit: Emitter, control: JobControl) {
        // The payload expression's column scope is the flat part AS RECEIVED — captured before the formulas'
        // flatView() below can materialize one from the payload.
        val receivedFlat = element.flat

        var newPayload: Any? = null
        if (payloadTransform) {
            val payloadHeader = receivedFlat?.header ?: HeaderListing.empty
            if (compiledPayload == null || payloadHeader != compiledPayloadForHeader) {
                compilePayload(payloadHeader, control)
            }
            newPayload = compiledPayload!!.evaluateRaw(
                element.payload, receivedFlat?.record ?: emptyRecord, payloadHeader)
        }

        if (formulaEntries.isNotEmpty()) {
            val flat = element.flatView()
            val header = flat.header
            if (header != compiledForHeader) {
                compileFormulas(header, control)
            }

            val record = flat.record
            for (i in compiledColumns.indices) {
                // Formulas see the ORIGINAL columns and the ORIGINAL payload (values append together below).
                formulaValues[i] = ColumnValue.toText(
                    compiledColumns[i].evaluate(element.payload, record, header))
            }
            record.addAll(formulaValues)
            flat.header = augmentedHeader
        }

        if (payloadTransform) {
            element.payload = newPayload
        }
        computed += 1

        emit.send(element)
    }


    private suspend fun compileFormulas(header: HeaderListing, control: JobControl) {
        // The full expression scope: receiver = the lane's inferred payload type, columns bare, parameters
        // bare and typed; parameter values are run-constant, injected once per compiled instance (never
        // baked into the generated source).
        val parameters = control.parameters()
        val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
        val parameterValues = parameters.components.map { control.parameter(it.name.value) }
        compiledColumns = control.runBlockingIo {
            formulaEntries.map { (name, expression) ->
                calculatedColumnEval.create(
                    name, expression, header, receiverType, classLoader, parameters)
            }
        }
        compiledColumns.forEach { it.setParameters(parameterValues) }
        augmentedHeader = header.append(formulaNames)
        compiledForHeader = header
    }


    private suspend fun compilePayload(header: HeaderListing, control: JobControl) {
        val parameters = control.parameters()
        val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
        val compiled = control.runBlockingIo {
            calculatedColumnEval.create(
                selfLocation.objectPath.name.value, payload, header, receiverType, classLoader, parameters)
        }
        compiled.setParameters(parameters.components.map { control.parameter(it.name.value) })
        compiledPayload = compiled
        compiledPayloadForHeader = header
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The walk contribution: the formulas append their names to a known column set, and a non-blank payload
    // expression re-types the lane's payload — inferred by the SAME (cached) compile the runtime will load,
    // where the static scope is known (input columns known; unknown approximates to nullable Any, validated
    // at run time as before). The first expression error becomes this Worker's validation error.
    override fun payloadFlow(input: WorkerLane, context: WorkerLaneContext): WorkerLaneAttempt {
        val receiverType = input.payloadType ?: TypeMetadata.anyNullable

        // Flat value formulas: compile-check against the auto-flattened consumer view when it is known.
        val formulaColumns = input.consumerFlatColumns()
        var errorMessage: String? = null
        if (formulaColumns != null) {
            for ((name, expression) in formulaEntries) {
                val error = calculatedColumnEval.validate(
                    name, expression, formulaColumns, receiverType, context.classLoader, context.parameters)
                if (error != null) {
                    errorMessage = "$name: $error"
                    break
                }
            }
        }

        val outputColumns = input.flatColumns
            ?.let {
                if (formulaEntries.isEmpty()) {
                    it
                }
                else {
                    // The formulas materialize the auto-flatten view before appending, so the output columns
                    // grow from what a consumer sees (unknown when that view is: a Map / untyped payload).
                    input.consumerFlatColumns()?.append(formulaNames)
                }
            }

        if (! payloadTransform) {
            return WorkerLaneAttempt(WorkerLane(input.payloadType, outputColumns), errorMessage)
        }

        val payloadColumns = input.flatColumns
            ?: return WorkerLaneAttempt(WorkerLane(TypeMetadata.anyNullable, outputColumns), errorMessage)

        val payloadError = calculatedColumnEval.validate(
            selfLocation.objectPath.name.value, payload, payloadColumns,
            receiverType, context.classLoader, context.parameters)
        if (payloadError != null) {
            return WorkerLaneAttempt(
                WorkerLane(null, outputColumns), errorMessage ?: payloadError)
        }

        val compiled = calculatedColumnEval.create(
            selfLocation.objectPath.name.value, payload, payloadColumns,
            receiverType, context.classLoader, context.parameters)
        val payloadType = calculatedColumnEval.inferredReturnKType(compiled)
            ?.let { ExpressionReturnTypeInference.toTypeMetadata(it, context.objectRegistryScan) }
            ?: TypeMetadata.anyNullable

        return WorkerLaneAttempt(WorkerLane(payloadType, outputColumns), errorMessage)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("computed" to computed)
}
