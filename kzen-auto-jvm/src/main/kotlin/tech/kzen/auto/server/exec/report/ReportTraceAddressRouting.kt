package tech.kzen.auto.server.exec.report

import tech.kzen.auto.server.exec.LogicTraceAddressRouting
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.service.store.normal.ObjectStableId


// Routes a Report's input / output progress marker to the literal trace path carried in the remaining address
// segments — Report's trace paths are by-convention, not per-element, so there is no stable-id translation.
object ReportTraceAddressRouting: LogicTraceAddressRouting {
    override val marker = ExecutionLogicTraceHandle.tracePathAddressMarker

    override fun tracePath(address: Address, stableId: ObjectStableId): LogicTracePath {
        return LogicTracePath(address.segments.drop(1))
    }
}
