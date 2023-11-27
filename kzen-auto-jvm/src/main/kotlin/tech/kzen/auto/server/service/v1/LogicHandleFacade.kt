package tech.kzen.auto.server.service.v1

import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.location.ObjectLocation


class LogicHandleFacade(
    private val logicRunExecutionId: LogicRunExecutionId,
    private val logicHandle: LogicHandle
) {
    fun start(
        originalObjectLocation: ObjectLocation
    ): LogicExecutionFacade {
        return logicHandle.start(logicRunExecutionId, originalObjectLocation)
    }
}