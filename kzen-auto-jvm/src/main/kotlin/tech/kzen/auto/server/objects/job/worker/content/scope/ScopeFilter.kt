package tech.kzen.auto.server.objects.job.worker.content.scope

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.expression.JobExpressionCompiler
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import java.math.BigDecimal


/**
 * A `Filter` inside an entry scope (spike): the same contract-native Kotlin predicate path as
 * [tech.kzen.auto.server.objects.job.worker.FilterWorker], compiled against the element's contract (the
 * [tech.kzen.auto.server.objects.job.worker.content.Entry] shape, so `name` and `size` bind by name) and
 * recompiled only when that contract changes. A kept element is forwarded untouched; an empty [where] keeps
 * everything. Holds nothing past the callback, so it is scope-compatible by construction (design §6.2).
 */
@Reflect
class ScopeFilter(
    private val where: String,
    @Service private val jobExpressionCompiler: JobExpressionCompiler
):
    ScopeBodyWorker
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val passThrough = where.isBlank()

    private var compiledForContract: DataContract? = null
    private var compiled: JobExpressionCompiler.Compiled? = null


    override suspend fun onElement(element: DataValue, emit: BodyEmitter, control: JobControl) {
        if (passThrough) {
            emit.send(element)
            return
        }

        val contract = element.contract
        if (contract != compiledForContract) {
            val parameters = control.parameters()
            compiled = control.runBlockingIo {
                val attempt = jobExpressionCompiler.compile(
                    "filter", where, contract, TypeMetadata.anyNullable, classLoader, parameters)
                check(attempt.error == null) { attempt.error ?: "Unable to compile filter expression" }
                checkNotNull(attempt.compiled)
            }
            compiled!!.expression.setParameters(
                parameters.definitions.map { control.parameter(it.name.value) })
            compiledForContract = contract
        }

        val projection = when (contract.structural) {
            is DataType.Record, is DataType.Scalar -> JobDataValues.projection(element)
            else -> null
        }
        val result = compiled!!.expression.evaluate(JobDataValues.native(element), element, projection)
        if (truthy(result)) {
            emit.send(element)
        }
    }


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
