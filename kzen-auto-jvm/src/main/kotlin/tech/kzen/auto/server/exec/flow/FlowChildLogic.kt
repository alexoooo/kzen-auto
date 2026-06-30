package tech.kzen.auto.server.exec.flow

import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * A [RunLogicVertex][tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex]'s pre-compiled callee, ready
 * to be [hosted][tech.kzen.lib.common.exec.engine.Execution.host] when the vertex runs: the child [logic],
 * the [childStableId] it is hosted under (the callee document's `main`, for the engine's execution tree), and
 * the [firstParameterName] the vertex's single upstream message is bound to (null when the callee declares no
 * parameters).
 */
class FlowChildLogic(
    val childStableId: ObjectStableId,
    val logic: Logic,
    val firstParameterName: TupleComponentName?
)
