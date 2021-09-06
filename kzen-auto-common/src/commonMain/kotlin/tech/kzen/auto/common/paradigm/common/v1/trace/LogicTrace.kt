package tech.kzen.auto.common.paradigm.common.v1.trace

import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceQuery
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot


interface LogicTrace {
//    fun mostRecent(): LogicRunId?


    // TODO: add logicExecutionNumber: Long ?
    fun lookup(
        logicRunExecutionId: LogicRunExecutionId,
        logicTraceQuery: LogicTraceQuery
    ): LogicTraceSnapshot?
}