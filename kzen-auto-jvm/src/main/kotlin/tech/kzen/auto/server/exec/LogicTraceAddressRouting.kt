package tech.kzen.auto.server.exec

import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Routes a reserved trace-address marker — the leading [Address] segment a run flavour emits for a
 * non-per-element trace value — to the [LogicTracePath] the value should be written at. Each flavour that emits
 * such a marker contributes one implementation (owned by its own module); the set is assembled at the
 * composition root and dispatched by ServerLogicController's trace bridge by marker, so the generic run
 * controller carries no per-flavour `when` (see CC-17). A flavour that emits only ordinary per-element
 * stable-id addresses needs none — the bridge's default stable-id path applies.
 */
interface LogicTraceAddressRouting {
    // The reserved leading address segment this routing claims (e.g. "$next-step", "$job-progress").
    val marker: String

    // Where to write the event's value, given its full within-node [address] and the emitting node's stable id.
    fun tracePath(address: Address, stableId: ObjectStableId): LogicTracePath
}
