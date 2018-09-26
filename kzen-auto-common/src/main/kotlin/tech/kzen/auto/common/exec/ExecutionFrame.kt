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
//        val value = values.remove(from)
//        if (value != null) {
////            println("!@!@! ExecutionFrame - $from -> $to | $value")
//
//            values[to] = value
//            return true
//        }
//
//        return false
    }
}
