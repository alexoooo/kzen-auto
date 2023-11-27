package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.auto.common.objects.document.sequence.model.StepTrace
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue


data class ActiveStepModel(
    var value: TupleValue? = null,
    var displayValue: ExecutionValue = NullExecutionValue,
    var detail: ExecutionValue = NullExecutionValue,
    var traceState: StepTrace.State = StepTrace.State.Idle,
    var error: String? = null
) {
    fun reset() {
        value = null
        displayValue = NullExecutionValue
        detail = NullExecutionValue
        traceState = StepTrace.State.Idle
        error = null
    }

    fun trace(): StepTrace {
        return StepTrace(
            traceState,
            displayValue,
            detail,
            error)
    }
}