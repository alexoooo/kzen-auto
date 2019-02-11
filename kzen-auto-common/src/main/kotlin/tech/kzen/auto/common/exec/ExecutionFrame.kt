package tech.kzen.auto.common.exec

import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.api.model.ObjectPath


data class ExecutionFrame(
        val path: BundlePath,
        val states: MutableMap<ObjectPath, ExecutionState>
) {
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
        check(objectPath !in states)
        states[objectPath] = ExecutionState.initial
    }
}
