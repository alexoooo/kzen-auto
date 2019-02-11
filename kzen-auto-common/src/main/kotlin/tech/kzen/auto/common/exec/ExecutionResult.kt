package tech.kzen.auto.common.exec


sealed class ExecutionResult


data class ExecutionError(
        val errorMessage: String
): ExecutionResult()


data class ExecutionSuccess(
        val value: Any?,
        val detail: Any?
): ExecutionResult() {
    companion object {
        val empty = ExecutionSuccess(null, null)
    }
}

