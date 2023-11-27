package tech.kzen.auto.server.service.v1

import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.location.ObjectLocation


interface LogicHandle {
    fun start(
        logicRunExecutionId: LogicRunExecutionId,
        originalObjectLocation: ObjectLocation
    ): LogicExecutionFacade
}