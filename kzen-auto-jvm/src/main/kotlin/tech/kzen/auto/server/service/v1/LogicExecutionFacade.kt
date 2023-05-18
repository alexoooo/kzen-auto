package tech.kzen.auto.server.service.v1

import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue


interface LogicExecutionFacade: AutoCloseable {
    fun beforeStart(arguments: TupleValue): Boolean

    fun continueOrStart(): LogicResult
}