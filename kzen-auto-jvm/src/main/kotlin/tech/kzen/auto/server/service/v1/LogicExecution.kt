package tech.kzen.auto.server.service.v1

import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.TupleValue


interface LogicExecution {
    fun next(arguments: TupleValue): LogicResult

    fun run(control: LogicControl): LogicResult

    fun step(control: LogicControl): LogicResult {
        return run(control)
    }
}