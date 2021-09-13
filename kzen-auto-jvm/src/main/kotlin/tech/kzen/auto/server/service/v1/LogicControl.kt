package tech.kzen.auto.server.service.v1

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.server.service.v1.model.LogicCommand


interface LogicControl {
//    fun arguments(): TupleValue


    fun pollCommand(): LogicCommand


    fun subscribeRequest(subscriber: (ExecutionRequest) -> ExecutionResult)
}