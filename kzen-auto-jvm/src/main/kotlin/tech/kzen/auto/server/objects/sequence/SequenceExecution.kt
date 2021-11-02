package tech.kzen.auto.server.objects.sequence

import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.TupleValue


class SequenceExecution:
    LogicExecution
{

    override fun next(arguments: TupleValue): Boolean {
        TODO("Not yet implemented")
    }


    override fun run(control: LogicControl): LogicResult {
        TODO("Not yet implemented")
    }


    override fun close(error: Boolean) {
        TODO("Not yet implemented")
    }
}