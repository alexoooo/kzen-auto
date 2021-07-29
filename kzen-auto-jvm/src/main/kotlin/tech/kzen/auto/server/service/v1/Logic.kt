package tech.kzen.auto.server.service.v1

import tech.kzen.auto.server.service.v1.model.LogicDefinition


interface Logic {
    fun define(): LogicDefinition
    fun execute(handle: LogicHandle): LogicExecution
}