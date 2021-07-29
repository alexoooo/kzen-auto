package tech.kzen.auto.server.service.v1.model.context

import tech.kzen.auto.server.service.v1.Logic
import tech.kzen.lib.common.service.store.normal.ObjectStableId


data class LogicContext(
    val logicInstances: Map<ObjectStableId, Logic>,
    val root: LogicFrame
)