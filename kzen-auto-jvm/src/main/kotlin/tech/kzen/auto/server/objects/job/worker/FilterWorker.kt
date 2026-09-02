package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.expression.JobExpressionCompiler
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import java.math.BigDecimal


/**
 * The filter stage as a Job Worker — the dataflow's predicate stage. Rather than a hardcoded column/value
 * comparison, [where] is an arbitrary Kotlin BOOLEAN EXPRESSION evaluated against each message with the full
 * expression scope: the lane's INFERRED PAYLOAD TYPE as the receiver (a typed payload's members are bare —
 * `age > 30` — with the `payload` alias as the escape hatch; the type comes from [JobControl.payloadType],
 * the same static walk the editor displays), the record fields bare by name (`City == "Lviv"`,
 * `amount > 30`, …), and the Job's declared parameters bare and typed (`value > threshold`, values read
 * via [JobControl.parameter]). It is compiled by the same contract-native [JobExpressionCompiler] used by
 * [FormulaWorker]. A scalar payload exposes its typed `value` accessor, so the predicate works over any stream.
 *
 * The predicate is compiled lazily and recompiled only when the incoming contract changes; a message is kept
 * when the compiled expression's result is truthy. An empty [where] keeps every message
 * (the batch passes through untouched). The RECEIVED message is forwarded (payload intact); an all-dropped
 * batch is skipped.
 *
 * A [TransformWorker]: the framework owns the drain loop, per-batch checkpoint, throttled progress, and
 * end-of-stream close propagation; this Worker only maps each batch.
 */
@Reflect
class FilterWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,

    private val where: String,
    selfLocation: ObjectLocation,

    @Service private val jobExpressionCompiler: JobExpressionCompiler
):
    TransformWorker(input, output, selfLocation)
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val passThrough = where.isBlank()

    private var compiledForContract: DataContract? = null
    private var compiled: JobExpressionCompiler.Compiled? = null

    private var seen = 0L
    private var kept = 0L


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        seen += 1

        if (passThrough) {
            kept += 1
            emit.send(element)
            return
        }

        val inputContract = control.inputContract()
            ?.takeUnless { it.structural is DataType.Dynamic }
            ?: element.contract
        if (inputContract != compiledForContract) {
            val parameters = control.parameters()
            val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
            compiled = control.runBlockingIo {
                val attempt = jobExpressionCompiler.compile(
                    "filter", where, inputContract, receiverType, classLoader, parameters)
                check(attempt.error == null) { attempt.error ?: "Unable to compile filter expression" }
                checkNotNull(attempt.compiled)
            }
            compiled!!.expression.setParameters(
                parameters.definitions.map { control.parameter(it.name.value) })
            compiledForContract = inputContract
        }

        val projection = when (inputContract.structural) {
            is DataType.Record, is DataType.Scalar -> JobDataValues.projection(element)
            else -> null
        }
        val result = compiled!!.expression.evaluate(JobDataValues.native(element), element, projection)
        if (truthy(result)) {
            kept += 1
            emit.send(element)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A filter forwards the received message untouched. Known contracts receive full static validation;
    // Dynamic contracts receive syntax-only validation until runtime supplies an element contract.
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        if (passThrough) {
            return JobLaneAttempt(input, null)
        }
        if (input.contract.structural is DataType.Dynamic) {
            return JobLaneAttempt(input, jobExpressionCompiler.validateSyntax(where))
        }

        val attempt = jobExpressionCompiler.compile(
            "filter", where, input.contract,
            input.payloadType ?: TypeMetadata.anyNullable,
            context.classLoader, context.parameters)
        return JobLaneAttempt(input, attempt.error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("seen" to seen, "kept" to kept)


    private fun truthy(value: Any?): Boolean =
        when (value) {
            true -> true
            is BigDecimal -> value.compareTo(BigDecimal.ONE) == 0
            is Number -> value.toDouble() == 1.0
            is Char -> value == 'y' || value == 'Y'
            is String -> value.equals("true", true) || value.equals("yes", true) ||
                    value == "y" || value == "Y"
            else -> false
        }
}
