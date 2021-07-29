package tech.kzen.auto.server.service.v1

import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.lib.common.model.locate.ObjectLocation


interface LogicHandle {
    interface Execution {
        fun next(arguments: TupleValue): LogicResult
        fun run(): LogicResult
    }

    fun start(originalObjectLocation: ObjectLocation): Execution
}