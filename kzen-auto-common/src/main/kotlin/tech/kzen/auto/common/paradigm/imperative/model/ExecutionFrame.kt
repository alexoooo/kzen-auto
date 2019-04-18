package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class ExecutionFrame(
        val path: DocumentPath,
        val states: PersistentMap<ObjectPath, ExecutionState>
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

            return ExecutionFrame(path, values.toPersistentMap())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun contains(objectPath: ObjectPath): Boolean {
        return states.containsKey(objectPath)
    }


    fun set(objectPath: ObjectPath, executionState: ExecutionState): ExecutionFrame {
        return copy(states = states.put(objectPath, executionState))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: ObjectPath, newName: ObjectName): ExecutionFrame {
        val state = states[from]
                ?: return this

        val newNamePath = from.copy(name = newName)

        val removedAtOldName = states.remove(from)
        val addedAtNewName = removedAtOldName.put(newNamePath, state)

        return copy(states = addedAtNewName)
//        val renamed = mutableMapOf<ObjectPath, ExecutionState>()
//        for (e in states) {
//            val key =
//                    if (e.key == from) {
//                        from.copy(name = newName)
//                    }
//                    else {
//                        e.key
//                    }
//
//            renamed[key] = e.value
//        }
//
//        states.clear()
//        states.putAll(renamed)
//
//        return true
    }


    fun add(objectPath: ObjectPath/*, index: Int*/): ExecutionFrame {
        check(objectPath !in states) { "Already present: $objectPath" }

        return copy(states = states.put(
                objectPath, ExecutionState.initial))
//        val added = mutableMapOf<ObjectPath, ExecutionState>()
//
//        for ((i, entry) in states.entries.withIndex()) {
//            if (i == index) {
//                added[objectPath] = ExecutionState.initial
//            }
//            added[entry.key] = entry.value
//        }
//
//        if (added.size == states.size) {
//            added[objectPath] = ExecutionState.initial
//        }
//
//        states.clear()
//        states.putAll(added)
    }


    fun remove(objectPath: ObjectPath): ExecutionFrame {
//        val previous = states.remove(objectPath)
//        return previous != null
        return copy(states = states.remove(objectPath))
    }
}
