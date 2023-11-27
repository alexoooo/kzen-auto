package tech.kzen.auto.common.paradigm.logic.trace

import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTraceQuery
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTraceSnapshot
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