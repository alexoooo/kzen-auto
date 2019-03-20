package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.api.model.ObjectPath


data class ExecutionFrame(
        val path: DocumentPath,
        val states: MutableMap<ObjectPath, ExecutionState>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val pathKey = "path"
        private const val statesKey = "states"


        fun toCollection(frame: ExecutionFrame): Map<String, Any> {
            val builder = mutableMapOf<String, Any>()

            builder[pathKey] = frame.path.asRelativeFile()

            val values = mutableMapOf<String, Map<String, Any?>>()
            for (e in frame.states) {
                values[e.key.asString()] = ExecutionState.toCollection(e.value)
            }
            builder[statesKey] = values

            return builder
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(
                collection: Map<String, Any>
        ): ExecutionFrame {
            val relativeLocation = collection[pathKey] as String
            val path = DocumentPath.parse(relativeLocation)

            val valuesMap = collection[statesKey] as Map<*, *>

            val values = mutableMapOf<ObjectPath, ExecutionState>()
            for (e in valuesMap) {
                values[ObjectPath.parse(e.key as String)] =
                        ExecutionState.fromCollection(e.value as Map<String, Any?>)
            }

            return ExecutionFrame(path, values)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun contains(objectName: ObjectPath): Boolean {
        return states.containsKey(objectName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: ObjectPath, newName: ObjectName): Boolean {
        if (! states.containsKey(from)) {
            return false
        }

        val renamed = mutableMapOf<ObjectPath, ExecutionState>()
        for (e in states) {
            val key =
                    if (e.key == from) {
                        from.copy(name = newName)
                    }
                    else {
                        e.key
                    }

            renamed[key] = e.value
        }

        states.clear()
        states.putAll(renamed)

        return true
    }


    fun add(objectPath: ObjectPath) {
        check(objectPath !in states) { "Already present: $objectPath" }
        states[objectPath] = ExecutionState.initial
    }


    fun remove(objectPath: ObjectPath): Boolean {
        val previous = states.remove(objectPath)
        return previous != null
    }
}
