package tech.kzen.auto.common.exec

import tech.kzen.lib.common.notation.model.ProjectPath


data class ExecutionFrame(
        val path: ProjectPath,
        var current: String,
        var status: ExecutionStatus
) {
    fun rename(from: String, to: String): ExecutionFrame {
        if (current != from) {
            return this
        }

        return ExecutionFrame(
                path,
                to,
                status)
    }
}
