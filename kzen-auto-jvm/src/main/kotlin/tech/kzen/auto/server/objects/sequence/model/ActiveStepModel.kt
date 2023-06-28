package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.sequence.StepTrace


data class ActiveStepModel(
    var value: Any? = null,
    var displayValue: ExecutionValue = NullExecutionValue,
    var detail: ExecutionValue = NullExecutionValue,
    var traceState: StepTrace.State = StepTrace.State.Idle,
    var error: String? = null
) {
    fun trace(): StepTrace {
        return StepTrace(
            traceState,
            displayValue,
            detail,
            error)
    }
}