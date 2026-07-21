package tech.kzen.auto.server.exec.flow

import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * A [FlowLogicHost][tech.kzen.auto.common.paradigm.flow.api.FlowLogicHost] vertex's pre-compiled callee, ready
 * to be [hosted][tech.kzen.lib.common.exec.engine.Execution.host] when the vertex runs: the child [logic], the
 * [childStableId] it is hosted under (the callee document's `main`, for the engine's execution tree), and the
 * callee's declared [parameterNames] in signature order — the leading ones take the vertex's wired inputs
 * positionally, the rest are addressable by name from the vertex's `arguments` (see the capability's binding
 * rule). Empty when the callee declares no parameters.
 */
class FlowChildLogic(
    val childStableId: ObjectStableId,
    val logic: Logic,
    val parameterNames: List<TupleComponentName>
)
