package tech.kzen.auto.server.exec.report

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Adapts the Report pipeline's trace writers ([tech.kzen.auto.server.objects.report.exec.trace.ReportInputTrace]
 * / [tech.kzen.auto.server.objects.report.exec.trace.ReportOutputTrace], which only ever touch a
 * [LogicTraceHandle]) onto the new engine's [Execution.emit]. Report's trace paths are LITERAL
 * ([tech.kzen.auto.common.objects.document.report.ReportConventions.inputTracePath] /
 * [tech.kzen.auto.common.objects.document.report.ReportConventions.outputTracePath]), not per-element stable
 * ids — so each [set] is emitted under the reserved [tracePathAddressMarker] address with the literal path
 * segments trailing, and the trace query view ([tech.kzen.auto.server.exec.RunEngineLogicTrace], via
 * [tech.kzen.auto.server.exec.report.ReportTraceAddressRouting]) translates it back to that [LogicTracePath]
 * at query time for the JS Report UI (symmetric with the Job worker-progress marker).
 *
 * NB: [emit] is called from disruptor consumer threads (output trace) as well as the run coroutine (input
 * trace); the engine's emit is lock-guarded, so this is safe. The pull-style [register] hook has no engine
 * equivalent (the bridge is push-only) — ReportOutputTrace pushes its count on update instead, so [register]
 * is a no-op here.
 */
class ExecutionLogicTraceHandle(
    private val execution: Execution
): LogicTraceHandle {
    companion object {
        // Reserved emit-address marker tagging a payload whose remaining address segments ARE the literal
        // LogicTracePath to set (vs. a per-stable-id step/vertex value). A stable id can never collide with it.
        const val tracePathAddressMarker = "\$trace-path"
    }


    override fun register(callback: (LogicTraceQuery) -> Unit): AutoCloseable {
        return AutoCloseable {}
    }


    override fun set(logicTracePath: LogicTracePath, executionValue: ExecutionValue) {
        execution.emit(
            Address.of(tracePathAddressMarker, *logicTracePath.segments.toTypedArray()),
            executionValue)
    }


    override fun append(objectStableId: ObjectStableId, value: ExecutionValue) {
        execution.log(value)
    }


    override fun clearAll(prefix: LogicTracePath) {
        // The controller clears the whole trace store on each run start; a mid-run clear has no engine analogue.
    }
}
