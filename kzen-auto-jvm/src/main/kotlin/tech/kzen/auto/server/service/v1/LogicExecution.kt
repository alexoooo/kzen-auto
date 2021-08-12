package tech.kzen.auto.server.service.v1

import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.TupleValue


interface LogicExecution {
    /**
     * Initialize for next execution
     */
    fun next(arguments: TupleValue)//: LogicResult


    /**
     * Continue running where we left off (possibly from beginning)
     */
    fun run(control: LogicControl): LogicResult


    /**
     * Continue running as little as possible from where we left off (paused state), and then pausing again
     */
    fun step(control: LogicControl): LogicResult {
        return run(control)
    }
}