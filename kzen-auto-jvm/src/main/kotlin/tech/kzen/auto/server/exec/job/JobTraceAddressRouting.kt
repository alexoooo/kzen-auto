package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.exec.LogicTraceAddressRouting
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.service.store.normal.ObjectStableId


// Routes a Job Worker's live-progress marker to that Worker's progress path, keyed by the emitting node's stable
// id (which IS the Worker's stable id) — the path the JS Job UI polls.
object JobTraceAddressRouting: LogicTraceAddressRouting {
    override val marker = EngineJobControl.workerProgressAddressMarker

    override fun tracePath(address: Address, stableId: ObjectStableId): LogicTracePath {
        return JobConventions.workerProgressPath(stableId)
    }
}
