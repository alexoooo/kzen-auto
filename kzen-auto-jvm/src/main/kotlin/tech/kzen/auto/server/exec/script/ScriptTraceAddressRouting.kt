package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.exec.LogicTraceAddressRouting
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.service.store.normal.ObjectStableId


// Routes a Script's "next to run" highlight marker to the fixed next-step trace path — a Script emits exactly
// one, so the address / stable id are irrelevant (Flow / Job / Report never emit it).
object ScriptTraceAddressRouting: LogicTraceAddressRouting {
    override val marker = ScriptRunContext.nextStepAddressMarker

    override fun tracePath(address: Address, stableId: ObjectStableId): LogicTracePath {
        return ScriptConventions.nextStepTracePath
    }
}
