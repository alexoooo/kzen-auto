package tech.kzen.auto.server.objects.sequence

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.TupleValue


class SequenceExecution:
    LogicExecution
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(SequenceExecution::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun next(arguments: TupleValue): Boolean {
        logger.info("arguments - {}", arguments)
        return true
    }


    override fun run(control: LogicControl): LogicResult {
        logger.info("run - {}", control.pollCommand())
        return LogicResultSuccess(TupleValue.ofMain(
            "foo"))
    }


    override fun close(error: Boolean) {
        logger.info("close - {}", error)
    }
}