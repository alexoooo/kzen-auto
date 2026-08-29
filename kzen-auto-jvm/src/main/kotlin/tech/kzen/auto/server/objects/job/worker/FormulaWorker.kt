package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.job.FormulaCarrySpec
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
import tech.kzen.auto.server.objects.job.value.CalculatedFieldValue
import tech.kzen.auto.server.objects.job.value.CarriedField
import tech.kzen.auto.server.objects.job.value.CarrySelection
import tech.kzen.auto.server.objects.job.value.FormulaValueTransformer
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.value.JobValueClaim
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataValue
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
 *   projects first, so formulas over a scalar stream see a synthetic `value` column. The
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
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,

    private val formula: FormulaSpec,
    private val payload: String,
    private val carry: FormulaCarrySpec,
    private val selfLocation: ObjectLocation,

    @Service private val calculatedColumnEval: CalculatedColumnEval
):
    TransformWorker(input, output, selfLocation)
{
    companion object {
        private val valueHeader = HeaderListing.ofUnique(listOf("value"))
    }

    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val formulaEntries = formula.formulas.entries.toList()
    private val formulaNames = HeaderListing.ofUnique(formulaEntries.map { it.key })
    private val formulaValues = Array(formulaEntries.size) { "" }
    private val payloadTransform = payload.isNotBlank()
    private val carrySelection = when {
        carry.all -> CarrySelection.All()
        carry.fields.isEmpty() -> CarrySelection.None
        else -> CarrySelection.Selected(carry.fields.map {
            CarriedField(parseFieldId(it.source), it.rename?.let(::parseFieldId))
        })
    }

    // The payload expression's record stand-in for a lane with no flat part (its compiled column scope is
    // empty there, so the record is never read).
    private val emptyRecord = FlatFileRecord()

    // Compiled lazily; recompiled only when the respective incoming header changes (HeaderListing
    // value-compare). The payload expression tracks its own header — its scope is the flat part AS RECEIVED,
    // not the auto-flattened view the formulas use.
    private var compiledForHeader: HeaderListing? = null
    private var compiledColumns: List<CalculatedColumn<Any?>> = listOf()
    private var compiledPayloadForHeader: HeaderListing? = null
    private var compiledPayload: CalculatedColumn<Any?>? = null

    private var computed = 0L


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        if (!payloadTransform && formulaEntries.isEmpty()) {
            emit.send(element)
            return
        }

        val originalPayload = JobDataValues.native(element)
        val previewProjection = JobDataValues.projection(element)
        val nativeMetadata = element.contract.nativeByPath[DataTypePath.root]
        val requiresSyntheticProjection =
            nativeMetadata != null &&
                element.access !is FlatFileRecord &&
                previewProjection.descriptor.columns.any { it.contract.structural !is DataType.Scalar }
        if (requiresSyntheticProjection) {
            if (formulaEntries.isNotEmpty() && valueHeader != compiledForHeader) {
                compileFormulas(valueHeader, control)
            }
            if (payloadTransform && (compiledPayload == null || HeaderListing.empty != compiledPayloadForHeader)) {
                compilePayload(HeaderListing.empty, control)
            }
            val formulaRecord = FlatFileRecord.of(ColumnValue.toText(originalPayload))
            val calculated = formulaEntries.indices.map { index ->
                ColumnValue.toText(compiledColumns[index].evaluate(originalPayload, formulaRecord, valueHeader))
            }
            val widened = JobDataValues.nativeRecord(
                valueHeader.append(formulaNames),
                FlatFileRecord.of(formulaRecord.toList() + calculated),
                checkNotNull(originalPayload),
                nativeMetadata)
            val replacement = if (payloadTransform) {
                JobDataValues.lift(compiledPayload!!.evaluateRaw(
                    originalPayload, emptyRecord, HeaderListing.empty))
            }
            else null
            val output = when {
                replacement == null -> widened
                carrySelection == CarrySelection.None -> replacement
                else -> FormulaValueTransformer.transform(
                    JobValueClaim(widened, exclusive = true),
                    calculate = { emptyList() },
                    replace = { replacement },
                    carry = carrySelection).value
            }
            computed += 1
            emit.send(output)
            return
        }
        if (formulaEntries.isNotEmpty() && previewProjection.header != compiledForHeader) {
            compileFormulas(previewProjection.header, control)
        }
        val structural = element.contract.structural
        val projectedPayloadScope = structural is DataType.Record || structural is DataType.Mapping
        val payloadHeader = if (projectedPayloadScope) previewProjection.header else HeaderListing.empty
        if (payloadTransform && (compiledPayload == null || payloadHeader != compiledPayloadForHeader)) {
            compilePayload(payloadHeader, control)
        }

        val result = FormulaValueTransformer.transform(
            JobValueClaim(element, exclusive = true),
            calculate = { projection ->
                val header = projection.header
                val record = JobDataValues.record(projection)
                formulaEntries.indices.map { index ->
                    formulaValues[index] = ColumnValue.toText(
                        compiledColumns[index].evaluate(originalPayload, record, header))
                    CalculatedFieldValue(
                        FieldId(formulaNames.values[index].text, formulaNames.values[index].occurrence),
                        DataType.Scalar(tech.kzen.lib.common.exec.data.type.ScalarKind.Text),
                        TextExecutionValue(formulaValues[index]))
                }
            },
            replace = if (payloadTransform) {{ projection ->
                val record = if (projectedPayloadScope) JobDataValues.record(projection) else emptyRecord
                JobDataValues.lift(compiledPayload!!.evaluateRaw(
                    originalPayload, record, payloadHeader))
            }} else null,
            carry = carrySelection)

        computed += 1
        emit.send(result.value)
    }


    private suspend fun compileFormulas(header: HeaderListing, control: JobControl) {
        // The full expression scope: receiver = the lane's inferred payload type, columns bare, parameters
        // bare and typed; parameter values are run-constant, injected once per compiled instance (never
        // baked into the generated source).
        val parameters = control.parameters()
        val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
        val parameterValues = parameters.definitions.map { control.parameter(it.name.value) }
        compiledColumns = control.runBlockingIo {
            formulaEntries.map { (name, expression) ->
                calculatedColumnEval.create(
                    name, expression, header, receiverType, classLoader, parameters)
            }
        }
        compiledColumns.forEach { it.setParameters(parameterValues) }
        compiledForHeader = header
    }


    private fun parseFieldId(encoded: String): FieldId {
        val delimiter = encoded.indexOf('|')
        if (delimiter > 0) {
            val occurrence = encoded.substring(0, delimiter).toIntOrNull()
            if (occurrence != null) {
                return FieldId(encoded.substring(delimiter + 1), occurrence)
            }
        }
        return FieldId(encoded, 0)
    }


    private suspend fun compilePayload(header: HeaderListing, control: JobControl) {
        val parameters = control.parameters()
        val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
        val compiled = control.runBlockingIo {
            calculatedColumnEval.create(
                selfLocation.objectPath.name.value, payload, header, receiverType, classLoader, parameters)
        }
        compiled.setParameters(parameters.definitions.map { control.parameter(it.name.value) })
        compiledPayload = compiled
        compiledPayloadForHeader = header
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The walk contribution: the formulas append their names to a known column set, and a non-blank payload
    // expression re-types the lane's payload — inferred by the SAME (cached) compile the runtime will load,
    // where the static scope is known (input columns known; unknown approximates to nullable Any, validated
    // at run time as before). The first expression error becomes this Worker's validation error.
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val receiverType = input.payloadType ?: TypeMetadata.anyNullable

        // Flat value formulas: compile-check against the auto-flattened consumer view where it is known,
        // otherwise syntax alone — the most that holds when the columns only arrive at run time.
        val formulaColumns = input.consumerFlatColumns()
        var errorMessage: String? = null
        for ((name, expression) in formulaEntries) {
            val error =
                if (formulaColumns != null) {
                    calculatedColumnEval.validate(
                        name, expression, formulaColumns, receiverType, context.classLoader, context.parameters)
                }
                else {
                    calculatedColumnEval.validateSyntax(expression)
                }
            if (error != null) {
                errorMessage = "$name: $error"
                break
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

        if (!payloadTransform) {
            return JobLaneAttempt(JobLaneDescriptor(input.payloadType, outputColumns), errorMessage)
        }

        // An unknown flat part leaves the payload expression's scope unknown too, so it degrades to syntax
        // alone; the resulting payload type is whatever the run infers.
        val payloadColumns = input.flatColumns
            ?: return JobLaneAttempt(
                JobLaneDescriptor(TypeMetadata.anyNullable, outputColumns),
                errorMessage ?: calculatedColumnEval.validateSyntax(payload))

        val payloadError = calculatedColumnEval.validate(
            selfLocation.objectPath.name.value, payload, payloadColumns,
            receiverType, context.classLoader, context.parameters)
        if (payloadError != null) {
            return JobLaneAttempt(
                JobLaneDescriptor(null, outputColumns), errorMessage ?: payloadError)
        }

        val compiled = calculatedColumnEval.create(
            selfLocation.objectPath.name.value, payload, payloadColumns,
            receiverType, context.classLoader, context.parameters)
        val payloadType = calculatedColumnEval.inferredReturnKType(compiled)
            ?.let(ExpressionReturnTypeInference::toTypeMetadata)
            ?: TypeMetadata.anyNullable

        return JobLaneAttempt(JobLaneDescriptor(payloadType, outputColumns), errorMessage)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("computed" to computed)
}
