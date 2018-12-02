package tech.kzen.auto.common.exec

import tech.kzen.lib.common.notation.model.ProjectPath


data class ExecutionFrame(
        val path: ProjectPath,
        val values: MutableMap<String, ExecutionStatus>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val pathKey = "path"
        private const val valuesKey = "values"


        fun toCollection(frame: ExecutionFrame): Map<String, Any> {
            val collection = mutableMapOf<String, Any>()

            collection[pathKey] = frame.path.relativeLocation

            val values = mutableMapOf<String, String>()
            for (e in frame.values) {
                values[e.key] = e.value.name
            }
            collection[valuesKey] = values

            return collection
        }


        fun fromCollection(collection: Map<String, Any>): ExecutionFrame {
            val relativeLocation = collection[pathKey] as String
            val path = ProjectPath(relativeLocation)

            val valuesMap = collection[valuesKey] as Map<*, *>

            val values = mutableMapOf<String, ExecutionStatus>()
            for (e in valuesMap) {
                values[e.key as String] = ExecutionStatus.valueOf(e.value as String)
            }

            return ExecutionFrame(path, values)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun contains(objectName: String): Boolean {
        return values.containsKey(objectName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: String, to: String): Boolean {
        if (! values.containsKey(from)) {
            return false
        }

        val renamed = mutableMapOf<String, ExecutionStatus>()
        for (e in values) {
            val key =
                    if (e.key == from)
                        to
                    else
                        e.key

            renamed[key] = e.value
        }

        values.clear()
        values.putAll(renamed)

        return true
    }
}
