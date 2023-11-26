package tech.kzen.auto.server.service.v1

import tech.kzen.auto.server.service.v1.model.LogicCommand
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult


interface LogicControl {
    fun pollCommand(): LogicCommand


    fun subscribeRequest(subscriber: (ExecutionRequest) -> ExecutionResult)
}