package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.expression.JobExpressionCompiler
import tech.kzen.auto.server.objects.job.expression.JobExpressionValues
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * Keeps or replaces each value's payload, and adds metadata. Each [formula] entry is a scalar expression whose
 * result becomes a metadata field of that name (replacing one the value already has); a non-blank [payload]
 * expression replaces the payload. Both see the incoming value, so neither observes the other's result, and the
 * output keeps the incoming metadata.
 */
@Reflect
class FormulaWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,

    private val formula: FormulaSpec,
    private val payload: String,
    private val selfLocation: ObjectLocation,

    @Service private val jobExpressionCompiler: JobExpressionCompiler
):
    TransformWorker(input, output, selfLocation)
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val formulaEntries = formula.formulas.entries.toList()
    private val formulaNames = HeaderListing.ofUnique(formulaEntries.map { it.key })
    private val formulaFields = formulaNames.values.map { FieldId(it.text, it.occurrence) }
    private val payloadTransform = payload.isNotBlank()

    private var compiledForContract: DataContract? = null
    private var compiledColumns: List<JobExpressionCompiler.Compiled> = listOf()
    private var compiledPayloadForContract: DataContract? = null
    private var compiledPayload: JobExpressionCompiler.Compiled? = null

    private var computed = 0L


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        if (!payloadTransform && formulaEntries.isEmpty()) {
            emit.send(element)
            return
        }

        val inputContract = control.inputContract()
            ?.takeUnless { it.structural is DataType.Dynamic }
            ?: element.contract
        if (formulaEntries.isNotEmpty() && inputContract != compiledForContract) {
            compileFormulas(inputContract, control)
        }
        if (payloadTransform && inputContract != compiledPayloadForContract) {
            compilePayload(inputContract, control)
        }

        val originalPayload = JobDataValues.native(element)
        val projection = when (inputContract.structural) {
            is DataType.Record, is DataType.Scalar -> JobDataValues.projection(element)
            else -> null
        }

        val calculated = formulaEntries.indices.map { index ->
            val compiled = compiledColumns[index]
            val scalarType = compiled.contract.structural as DataType.Scalar
            val (_, encoded) = JobExpressionValues.scalar(
                compiled.expression.evaluate(originalPayload, element, projection),
                scalarType)
            formulaFields[index] to LiteralDataValues.lift(literal(encoded), DataContract(scalarType))
        }
        val outputPayload =
            if (payloadTransform) {
                val compiled = checkNotNull(compiledPayload)
                JobDataValues.lift(
                    compiled.expression.evaluate(originalPayload, element, projection),
                    compiled.contract)
            }
            else {
                element.payload()
            }
        val metadata =
            if (calculated.isEmpty()) element.metadata
            else ValueMetadata.of(DataOverlay.record(element.metadata?.value, calculated))

        computed += 1
        emit.send(inheriting(outputPayload.withMetadata(metadata), element, control))
    }


    // E9 item 3: the output may hold anything reachable from the input (a replaced payload, the kept one), so a
    // non-scalar output keeps the input's native open until its own consumer is done; a scalar carries no owner.
    // Nothing is copied or inspected — only the ledger's owner set is propagated.
    private fun inheriting(output: DataValue, input: DataValue, control: JobControl): DataValue {
        control.ownership()?.inherit(output, input)
        return output
    }


    /** The literal form of an encoded calculated scalar: metadata is plain data, with no JVM type of its own. */
    private fun literal(encoded: ScalarExecutionValue?): Any? =
        when (encoded) {
            null -> null
            is BooleanExecutionValue -> encoded.value
            is LongExecutionValue -> encoded.value
            is NumberExecutionValue -> encoded.value
            is TextExecutionValue -> encoded.value
            is BinaryExecutionValue -> encoded.value
            else -> error("Unexpected calculated value: $encoded")
        }


    private suspend fun compileFormulas(contract: DataContract, control: JobControl) {
        val parameters = control.parameters()
        val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
        val parameterValues = parameters.definitions.map { control.parameter(it.name.value) }
        compiledColumns = control.runBlockingIo {
            formulaEntries.map { (name, expression) ->
                val attempt = jobExpressionCompiler.compile(
                    name, expression, contract, receiverType, classLoader, parameters)
                check(attempt.error == null) { "$name: ${attempt.error}" }
                val compiled = checkNotNull(attempt.compiled)
                check(compiled.contract.structural is DataType.Scalar) {
                    "$name: calculated Job fields must be scalar, found ${compiled.contract.structural}"
                }
                compiled
            }
        }
        compiledColumns.forEach { it.expression.setParameters(parameterValues) }
        compiledForContract = contract
    }


    private suspend fun compilePayload(contract: DataContract, control: JobControl) {
        val parameters = control.parameters()
        val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
        val compiled = control.runBlockingIo {
            jobExpressionCompiler.compile(
                selfLocation.objectPath.name.value,
                payload,
                contract,
                receiverType,
                classLoader,
                parameters)
        }
        check(compiled.error == null) { compiled.error ?: "Unable to compile payload expression" }
        val expression = checkNotNull(compiled.compiled)
        expression.expression.setParameters(parameters.definitions.map { control.parameter(it.name.value) })
        compiledPayload = expression
        compiledPayloadForContract = contract
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        if (formulaEntries.isEmpty() && !payloadTransform) {
            return JobLaneAttempt(input, null)
        }

        if (input.contract.structural is DataType.Dynamic) {
            val syntaxError = formulaEntries.firstNotNullOfOrNull { (name, expression) ->
                jobExpressionCompiler.validateSyntax(expression)?.let { "$name: $it" }
            } ?: payload.takeIf { payloadTransform }?.let { jobExpressionCompiler.validateSyntax(it) }

            // A payload of unknown type still has known metadata; each calculated field's type waits for Run
            val calculated = formulaFields.map { it to DataContract(DataType.Dynamic()) }
            val outputPayload =
                if (payloadTransform) DataContract(DataType.Dynamic())
                else input.contract.payload()
            val output = outputPayload.withMetadataOf(input.contract, calculated)
            return JobLaneAttempt(JobLaneDescriptor(output), syntaxError)
        }

        val receiverType = input.payloadType ?: TypeMetadata.anyNullable
        val warnings = mutableListOf<String>()
        val calculated = mutableListOf<Pair<FieldId, DataContract>>()
        for ((index, entry) in formulaEntries.withIndex()) {
            val (name, expression) = entry
            val attempt = jobExpressionCompiler.compile(
                name, expression, input.contract, receiverType, context.classLoader, context.parameters)
            warnings += attempt.warnings.map { "$name: $it" }
            val compiled = attempt.compiled
                ?: return JobLaneAttempt(input, "$name: ${attempt.error ?: "Unable to compile"}")
            val scalar = compiled.contract.structural as? DataType.Scalar
                ?: return JobLaneAttempt(
                    input,
                    "$name: calculated Job fields must be scalar, found ${compiled.contract.structural}")
            calculated += formulaFields[index] to DataContract(scalar)
        }

        val outputPayload =
            if (payloadTransform) {
                val attempt = jobExpressionCompiler.compile(
                    selfLocation.objectPath.name.value,
                    payload,
                    input.contract,
                    receiverType,
                    context.classLoader,
                    context.parameters)
                warnings += attempt.warnings
                attempt.compiled?.contract
                    ?: return JobLaneAttempt(JobLaneDescriptor.unknown, attempt.error)
            }
            else {
                input.contract.payload()
            }

        val output = outputPayload.withMetadataOf(input.contract, calculated)
        return JobLaneAttempt(
            JobLaneDescriptor(output), null, warnings.joinToString("; ").ifBlank { null })
    }


    /** This payload contract with [input]'s metadata, plus the [calculated] fields set on it. */
    private fun DataContract.withMetadataOf(
        input: DataContract,
        calculated: List<Pair<FieldId, DataContract>>
    ): DataContract {
        val carried = input.metadata?.let { withMetadata(it) } ?: this
        return DataOverlay.withMetadataFields(carried, calculated)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("computed" to computed)
}
