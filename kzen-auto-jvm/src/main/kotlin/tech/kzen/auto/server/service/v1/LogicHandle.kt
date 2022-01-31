package tech.kzen.auto.server.service.v1

import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.lib.common.model.locate.ObjectLocation


interface LogicHandle {
    fun start(
        logicRunExecutionId: LogicRunExecutionId,
        originalObjectLocation: ObjectLocation
    ): LogicExecutionFacade
}