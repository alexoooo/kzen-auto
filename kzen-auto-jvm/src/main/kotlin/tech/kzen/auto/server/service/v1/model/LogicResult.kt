package tech.kzen.auto.server.service.v1.model


sealed class LogicResult


object LogicResultPaused: LogicResult()


object LogicResultCancelled: LogicResult()


data class LogicResultFailed(
    val message: String
): LogicResult()


data class LogicResultSuccess(
    val value: TupleValue
): LogicResult()

