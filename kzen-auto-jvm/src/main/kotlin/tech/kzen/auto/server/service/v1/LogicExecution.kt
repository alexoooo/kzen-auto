package tech.kzen.auto.server.service.v1

import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.TupleValue


interface LogicExecution {
    /**
     * Initialize for next execution
     * @return false if something went wrong
     */
    fun next(arguments: TupleValue): Boolean


    /**
     * Continue running where we left off (possibly from beginning)
     */
    fun run(control: LogicControl): LogicResult


    /**
     * Continue running as little as possible from where we left off (paused state or beginning),
     *  and then pause (again)
     */
    fun step(control: LogicControl): LogicResult {
        return run(control)
    }


    fun close(error: Boolean)
}