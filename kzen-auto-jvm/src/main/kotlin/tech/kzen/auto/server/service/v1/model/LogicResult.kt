package tech.kzen.auto.server.service.v1.model

import tech.kzen.auto.server.service.v1.model.tuple.TupleValue


sealed class LogicResult {
    abstract fun isTerminal(): Boolean
}


data object LogicResultPaused: LogicResult() {
    override fun isTerminal() = false
}


data object LogicResultCancelled: LogicResult() {
    override fun isTerminal() = true
}


data class LogicResultFailed(
    val message: String
): LogicResult() {
    override fun isTerminal() = true
}


data class LogicResultSuccess(
    val value: TupleValue
): LogicResult() {
    companion object {
        val empty = LogicResultSuccess(TupleValue.empty)
    }

    override fun isTerminal() = true
}

