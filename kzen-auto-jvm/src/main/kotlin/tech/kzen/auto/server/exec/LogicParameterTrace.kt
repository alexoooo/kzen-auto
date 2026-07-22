package tech.kzen.auto.server.exec

import tech.kzen.auto.common.util.TraceDisplay
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Surfaces each declared parameter's resolved run value to the trace, once at run (re)start, at the parameter's
 * own stable-id address — the flavour-neutral contract the client's signature editor reads to show the actual
 * run value beside the declared default. The emitted value is a bounded plain display string, NOT a Script
 * StepTrace: a parameter is not a step, and Job / Flow must not couple to the Script model. Null emits
 * [NullExecutionValue] — the editor hides it, while a re-run still overwrites a stale prior value at the same
 * address. Transient (retain = false): the live latest-value-per-address view is the only reader, so the emit
 * must not grow the run's history.
 */
object LogicParameterTrace {
    fun emitAll(execution: Execution, parameters: List<LogicParameter>) {
        for (parameter in parameters) {
            emit(execution, parameter.stableId, parameter.resolve(execution.inputs))
        }
    }


    fun emit(execution: Execution, stableId: ObjectStableId, value: Any?) {
        val display =
            if (value == null) {
                NullExecutionValue
            }
            else {
                ExecutionValue.of(TraceDisplay.truncatedToString(value, TraceDisplay.maxScriptTraceChars))
            }
        execution.emit(Address.of(stableId.value), display, retain = false)
    }
}
