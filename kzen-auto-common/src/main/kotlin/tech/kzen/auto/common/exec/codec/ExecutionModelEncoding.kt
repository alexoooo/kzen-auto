package tech.kzen.auto.common.exec.codec

import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.objects.service.ActionManager


data class ExecutionModelEncoding(
        val frames: List<ExecutionFrameEncoding>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun encode(
                executionModel: ExecutionModel,
                actionManager: ActionManager
        ): ExecutionModelEncoding {
            return ExecutionModelEncoding(
                    executionModel.frames.map { ExecutionFrameEncoding.encode(it, actionManager) }
            )
        }


        fun toCollection(model: ExecutionModelEncoding): List<Map<String, Any>> {
            return model
                    .frames
                    .map { ExecutionFrameEncoding.toCollection(it) }
        }


        fun fromCollection(
                collection: List<Map<String, Any>>
        ): ExecutionModelEncoding {
            return ExecutionModelEncoding(collection
                    .map { ExecutionFrameEncoding.fromCollection(it) }
                    .toList())
        }
    }


    fun decode(): ExecutionModel {
        return ExecutionModel(
                frames.map { it.decode() }.toMutableList()
        )
    }
}