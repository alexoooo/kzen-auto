package tech.kzen.auto.server.service.v1.model.context

import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunFrameInfo
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import java.util.concurrent.CopyOnWriteArrayList


data class LogicFrame(
    val logicId: ObjectStableId,
    val executionId: LogicExecutionId,
    val execution: LogicExecution,
    val dependencies: CopyOnWriteArrayList<LogicFrame>,

    // shared between all frames
    val control: MutableLogicControl
) {
    fun toInfo(
        objectStableMapper: ObjectStableMapper
    ): LogicRunFrameInfo {
        return LogicRunFrameInfo(
            objectStableMapper.objectLocation(logicId),
            executionId,
            dependencies.map { it.toInfo(objectStableMapper) }
        )
    }


    fun find(targetExecutionId: LogicExecutionId): LogicFrame? {
        if (executionId == targetExecutionId) {
            return this
        }

        for (dependency in dependencies) {
            val match = dependency.find(targetExecutionId)
            if (match != null) {
                return match
            }
        }

        return null
    }
}