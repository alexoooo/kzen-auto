package tech.kzen.auto.common.objects.document.job.model

import tech.kzen.auto.common.objects.document.logic.StepValidation
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.obj.ObjectPath


/**
 * A Job document's server-side validation result (the Script `ScriptValidation` analogue), keyed by Worker
 * object path: each entry's `typeMetadata` is the Worker's OUTPUT payload type per the static payload-type
 * walk (null = no payload to show — a flat/CSV lane), `flatColumns` its known ordered tabular labels, and
 * `errorMessage` its expression validation error.
 * Computed per notation version by the JobValidator detached action (cached), consumed by the Job editor's
 * worker cards.
 */
data class JobValidation(
    val workerValidations: Map<ObjectPath, StepValidation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = JobValidation(mapOf())


        fun ofExecutionValue(executionValue: MapExecutionValue): JobValidation {
            val workerValidations = executionValue
                .values
                .map {
                    ObjectPath.parse(it.key) to
                            StepValidation.ofMapExecutionValue(it.value as MapExecutionValue)
                }
                .toMap()

            return JobValidation(workerValidations)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(
            workerValidations.map {
                it.key.asString() to it.value.asExecutionValue()
            }.toMap())
    }
}
