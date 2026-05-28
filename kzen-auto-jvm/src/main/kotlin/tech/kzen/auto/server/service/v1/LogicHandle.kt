package tech.kzen.auto.server.service.v1

import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


interface LogicHandle {
    fun start(
        logicRunExecutionId: LogicRunExecutionId,
        originalObjectLocation: ObjectLocation,
        objectStableMapper: ObjectStableMapper
    ): LogicExecutionFacade
}