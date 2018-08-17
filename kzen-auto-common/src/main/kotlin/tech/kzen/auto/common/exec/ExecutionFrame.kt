package tech.kzen.auto.common.exec

import tech.kzen.lib.common.notation.model.ProjectPath


data class ExecutionFrame(
        val path: ProjectPath,
        val values: MutableMap<String, ExecutionStatus>
) {
    fun contains(objectName: String): Boolean {
        return values.containsKey(objectName)
    }


    fun rename(from: String, to: String): Boolean {
//        if (current == from) {
//            current = to
//        }

        val value = values.remove(from)
        if (value != null) {
            values[to] = value
            return true
        }

        return false
    }
}
