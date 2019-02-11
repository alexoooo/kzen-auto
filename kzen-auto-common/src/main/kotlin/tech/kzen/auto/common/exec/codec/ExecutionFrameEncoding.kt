package tech.kzen.auto.common.exec.codec

import tech.kzen.auto.common.exec.ExecutionFrame
import tech.kzen.auto.common.objects.service.ActionManager
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectPath


data class ExecutionFrameEncoding(
        val path: BundlePath,
        val states: Map<ObjectPath, ExecutionStateEncoding>
){
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val pathKey = "path"
        private const val statesKey = "states"


        fun encode(
                executionFrame: ExecutionFrame,
                actionManager: ActionManager
        ): ExecutionFrameEncoding {
            return ExecutionFrameEncoding(
                    executionFrame.path,
                    executionFrame.states.mapValues {
                        ExecutionStateEncoding.encode(
                                it.value,
                                ObjectLocation(executionFrame.path, it.key),
                                actionManager)
                    }
            )
        }


        fun toCollection(frame: ExecutionFrameEncoding): Map<String, Any> {
            val builder = mutableMapOf<String, Any>()

            builder[pathKey] = frame.path.asRelativeFile()

            val values = mutableMapOf<String, Map<String, Any?>>()
            for (e in frame.states) {
                values[e.key.asString()] = ExecutionStateEncoding.toCollection(e.value)
            }
            builder[statesKey] = values

            return builder
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(
                collection: Map<String, Any>
        ): ExecutionFrameEncoding {
            val relativeLocation = collection[pathKey] as String
            val path = BundlePath.parse(relativeLocation)

            val valuesMap = collection[statesKey] as Map<*, *>

            val values = mutableMapOf<ObjectPath, ExecutionStateEncoding>()
            for (e in valuesMap) {
                values[ObjectPath.parse(e.key as String)] =
                        ExecutionStateEncoding.fromCollection(e.value as Map<String, Any?>)
            }

            return ExecutionFrameEncoding(path, values)
        }
    }


    fun decode(): ExecutionFrame {
        return ExecutionFrame(
                path,
                states.mapValues { it.value.decode() }.toMutableMap())
    }
}