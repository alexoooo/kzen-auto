package tech.kzen.auto.server.objects.script.model

import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.tuple.TupleValue


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