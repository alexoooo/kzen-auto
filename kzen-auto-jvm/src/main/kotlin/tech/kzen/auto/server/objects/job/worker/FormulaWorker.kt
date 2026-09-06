package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.job.FormulaCarrySpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.job.expression.JobExpressionCompiler
import tech.kzen.auto.server.objects.job.expression.JobExpressionValues
import tech.kzen.auto.server.objects.job.value.CalculatedFieldValue
import tech.kzen.auto.server.objects.job.value.CarriedField
import tech.kzen.auto.server.objects.job.value.CarrySelection
import tech.kzen.auto.server.objects.job.value.ColumnProjection
import tech.kzen.auto.server.objects.job.value.FormulaValueTransformer
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.value.JobValueClaim
import tech.kzen.auto.server.objects.report.exec.calc.ColumnValue
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/** Contract-typed calculated fields and payload replacement over the incoming Job value. */
@Reflect
class FormulaWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,

    private val formula: FormulaSpec,
    private val payload: String,
    private val carry: FormulaCarrySpec,
    private val selfLocation: ObjectLocation,

    @Service private val jobExpressionCompiler: JobExpressionCompiler
):
    TransformWorker(input, output, selfLocation)
{
    companion object {
        private val valueHeader = HeaderListing.ofUnique(listOf("value"))

        private fun requiresSyntheticProjection(contract: DataContract): Boolean {
            if (contract.nativeByPath[DataTypePath.root] == null) return false
            return when (val structural = contract.structural) {
                is DataType.Record -> structural.fields.any { it.type !is DataType.Scalar }
                is DataType.Mapping -> structural.value !is DataType.Scalar
                is DataType.Scalar -> false
                else -> true
            }
        }
    }

    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val formulaEntries = formula.formulas.entries.toList()
    private val formulaNames = HeaderListing.ofUnique(formulaEntries.map { it.key })
    private val payloadTransform = payload.isNotBlank()
    private val carrySelection = when {
        carry.all -> CarrySelection.All()
        carry.fields.isEmpty() -> CarrySelection.None
        else -> CarrySelection.Selected(carry.fields.map {
            CarriedField(parseFieldId(it.source), it.rename?.let(::parseFieldId))
        })
    }

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

        val originalPayload = JobDataValues.native(element)
        val inputContract = control.inputContract()
            ?.takeUnless { it.structural is DataType.Dynamic }
            ?: element.contract
        if (formulaEntries.isNotEmpty() && inputContract != compiledForContract) {
            compileFormulas(inputContract, control)
        }
        if (payloadTransform && inputContract != compiledPayloadForContract) {
            compilePayload(inputContract, control)
        }

        val nativeMetadata = element.contract.nativeByPath[DataTypePath.root]
        val requiresSyntheticProjection =
            element.access !is FlatFileRecord && requiresSyntheticProjection(element.contract)
        if (requiresSyntheticProjection) {
            val synthetic = JobDataValues.nativeRecord(
                valueHeader,
                FlatFileRecord.of(ColumnValue.toText(originalPayload)),
                checkNotNull(originalPayload),
                checkNotNull(nativeMetadata))
            val evaluationProjection =
                if (element.contract.structural is DataType.Record) JobDataValues.projection(element)
                else JobDataValues.projection(synthetic)
            val calculated = calculatedFields(originalPayload, element, evaluationProjection)
            val replacement = if (payloadTransform) {
                replacement(originalPayload, element, evaluationProjection)
            }
            else {
                null
            }
            val result = FormulaValueTransformer.transform(
                JobValueClaim(synthetic, exclusive = true),
                calculate = { calculated },
                replace = replacement?.let { value -> { _: ColumnProjection -> value } },
                carry = carrySelection)
            computed += 1
            emit.send(inheriting(result.value, element, control))
            return
        }

        val result = FormulaValueTransformer.transform(
            JobValueClaim(element, exclusive = true),
            calculate = { projection -> calculatedFields(originalPayload, element, projection) },
            replace = if (payloadTransform) {{ projection ->
                replacement(originalPayload, element, projection)
            }} else null,
            carry = carrySelection)

        computed += 1
        emit.send(inheriting(result.value, element, control))
    }


    // E9 item 3: the output may hold anything reachable from the input (a replaced payload, a carried native
    // record), so a non-scalar output keeps the input's native open until its own consumer is done; a scalar
    // carries no owner. Nothing is copied or inspected — only the ledger's owner set is propagated.
    private fun inheriting(output: DataValue, input: DataValue, control: JobControl): DataValue {
        control.ownership()?.inherit(output, input)
        return output
    }


    private fun calculatedFields(
        originalPayload: Any?,
        element: DataValue,
        projection: ColumnProjection
    ): List<CalculatedFieldValue> = formulaEntries.indices.map { index ->
        val compiled = compiledColumns[index]
        val scalarType = compiled.contract.structural as DataType.Scalar
        val (state, encoded) = JobExpressionValues.scalar(
            compiled.expression.evaluate(originalPayload, element, projection),
            scalarType)
        CalculatedFieldValue(
            FieldId(formulaNames.values[index].text, formulaNames.values[index].occurrence),
            scalarType,
            encoded,
            state)
    }


    private fun replacement(
        originalPayload: Any?,
        element: DataValue,
        projection: ColumnProjection
    ): DataValue {
        val compiled = checkNotNull(compiledPayload)
        return JobDataValues.lift(
            compiled.expression.evaluate(originalPayload, element, projection),
            compiled.contract)
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


    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        if (input.contract.structural is DataType.Dynamic) {
            val formulaError = formulaEntries.firstNotNullOfOrNull { (name, expression) ->
                jobExpressionCompiler.validateSyntax(expression)?.let { "$name: $it" }
            }
            if (formulaError != null) {
                return JobLaneAttempt(input, formulaError)
            }
            val payloadError = if (payloadTransform) {
                jobExpressionCompiler.validateSyntax(payload)
            }
            else {
                null
            }
            val output = if (formulaEntries.isEmpty() && !payloadTransform) {
                input
            }
            else {
                JobLaneDescriptor.unknown
            }
            return JobLaneAttempt(output, payloadError)
        }

        val receiverType = input.payloadType ?: TypeMetadata.anyNullable
        val calculatedFields = mutableListOf<DataField>()
        for ((name, expression) in formulaEntries) {
            val attempt = jobExpressionCompiler.compile(
                name, expression, input.contract, receiverType, context.classLoader, context.parameters)
            val compiled = attempt.compiled
            if (compiled == null) {
                return JobLaneAttempt(input, "$name: ${attempt.error ?: "Unable to compile"}")
            }
            val scalar = compiled.contract.structural as? DataType.Scalar
                ?: return JobLaneAttempt(
                    input,
                    "$name: calculated Job fields must be scalar, found ${compiled.contract.structural}")
            val label = formulaNames.values[calculatedFields.size]
            calculatedFields += DataField(FieldId(label.text, label.occurrence), scalar)
        }
        val widened = appendCalculated(input, calculatedFields)
        if (!payloadTransform) {
            return JobLaneAttempt(widened, null)
        }

        val payloadAttempt = jobExpressionCompiler.compile(
            selfLocation.objectPath.name.value,
            payload,
            input.contract,
            receiverType,
            context.classLoader,
            context.parameters)
        val replacement = payloadAttempt.compiled
            ?: return JobLaneAttempt(JobLaneDescriptor.unknown, payloadAttempt.error)
        val replacementLane = JobLaneDescriptor(replacement.contract)
        return JobLaneAttempt(appendCarried(replacementLane, widened), null)
    }


    private fun appendCarried(
        replacement: JobLaneDescriptor,
        widened: JobLaneDescriptor
    ): JobLaneDescriptor {
        if (carrySelection == CarrySelection.None) {
            return replacement
        }
        val source = projectableFields(widened.contract) ?: return JobLaneDescriptor.unknown
        val carried = when (val selection = carrySelection) {
            CarrySelection.None -> emptyList()
            is CarrySelection.All -> source
            is CarrySelection.Selected -> {
                val byId = source.associateBy { it.id }
                selection.fields.map { selected ->
                    val field = requireNotNull(byId[selected.source]) {
                        "Unknown carry field '${selected.source}'"
                    }
                    selected.rename?.let { field.copy(id = it) } ?: field
                }
            }
        }
        val target = projectableFields(replacement.contract) ?: return JobLaneDescriptor.unknown
        val ids = target.mapTo(mutableSetOf()) { it.id }
        val collision = carried.firstOrNull { !ids.add(it.id) }
        require(collision == null) { "Carry field '${collision?.id}' collides with replacement output" }
        return JobLaneDescriptor(DataContract(
            DataType.Record(target + carried, replacement.contract.structural.nullable),
            replacement.contract.nativeByPath))
    }


    private fun projectableFields(contract: DataContract): List<DataField>? =
        when (val structural = contract.structural) {
            is DataType.Record -> structural.fields
            is DataType.Scalar -> listOf(DataField(FieldId("value"), structural))
            else -> null
        }


    private fun appendCalculated(
        input: JobLaneDescriptor,
        calculated: List<DataField>
    ): JobLaneDescriptor {
        if (calculated.isEmpty()) {
            return input
        }
        val existing = if (requiresSyntheticProjection(input.contract)) {
            listOf(DataField(FieldId("value"), DataType.Scalar(ScalarKind.Text)))
        }
        else when (val structural = input.contract.structural) {
            is DataType.Record -> structural.fields
            is DataType.Scalar -> listOf(DataField(FieldId("value"), structural))
            else -> return JobLaneDescriptor.unknown
        }
        val collisions = existing.mapTo(mutableSetOf()) { it.id }
        val duplicate = calculated.firstOrNull { !collisions.add(it.id) }
        require(duplicate == null) { "Calculated field '${duplicate?.id}' collides with an input field" }
        return JobLaneDescriptor(DataContract(
            DataType.Record(existing + calculated, input.contract.structural.nullable),
            input.contract.nativeByPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("computed" to computed)
}
