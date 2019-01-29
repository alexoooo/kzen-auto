package tech.kzen.auto.common.exec

import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.api.model.ObjectPath


data class ExecutionFrame(
        val path: BundlePath,
        val values: MutableMap<ObjectPath, ExecutionStatus>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val pathKey = "path"
        private const val valuesKey = "values"


        fun toCollection(frame: ExecutionFrame): Map<String, Any> {
            val collection = mutableMapOf<String, Any>()

            collection[pathKey] = frame.path.asRelativeFile()

            val values = mutableMapOf<String, String>()
            for (e in frame.values) {
                values[e.key.asString()] = e.value.name
            }
            collection[valuesKey] = values

            return collection
        }


        fun fromCollection(collection: Map<String, Any>): ExecutionFrame {
            val relativeLocation = collection[pathKey] as String
            val path = BundlePath.parse(relativeLocation)

            val valuesMap = collection[valuesKey] as Map<*, *>

            val values = mutableMapOf<ObjectPath, ExecutionStatus>()
            for (e in valuesMap) {
                values[ObjectPath.parse(e.key as String)] =
                        ExecutionStatus.valueOf(e.value as String)
            }

            return ExecutionFrame(path, values)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun contains(objectName: ObjectPath): Boolean {
        return values.containsKey(objectName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: ObjectPath, newName: ObjectName): Boolean {
        if (! values.containsKey(from)) {
            return false
        }

        val renamed = mutableMapOf<ObjectPath, ExecutionStatus>()
        for (e in values) {
            val key =
                    if (e.key == from) {
                        from.copy(name = newName)
                    }
                    else {
                        e.key
                    }

            renamed[key] = e.value
        }

        values.clear()
        values.putAll(renamed)

        return true
    }
}
