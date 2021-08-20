package tech.kzen.auto.common.paradigm.common.v1.trace

import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceQuery
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot


interface LogicTrace {
//    fun mostRecent(): LogicRunId?


    // TODO: add logicExecutionNumber: Long ?
    fun lookup(
        logicRunId: LogicRunId,
        logicExecutionId: LogicExecutionId,
        logicTraceQuery: LogicTraceQuery
    ): LogicTraceSnapshot?
}