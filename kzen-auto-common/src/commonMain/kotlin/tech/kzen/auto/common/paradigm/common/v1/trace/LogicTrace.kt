package tech.kzen.auto.common.paradigm.common.v1.trace

import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceQuery
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.ObjectLocation


interface LogicTrace {
    fun mostRecent(
        objectLocation: ObjectLocation
    ): LogicRunExecutionId?


    fun clear(
        objectLocation: ObjectLocation
    ): Boolean


    fun lookup(
        logicRunExecutionId: LogicRunExecutionId,
        logicTraceQuery: LogicTraceQuery
    ): LogicTraceSnapshot?
}