package tech.kzen.auto.server.service.v1

import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition


interface LogicExecution {
    /**
     * Initialize for next execution
     * @return false if something went wrong
     */
    fun beforeStart(arguments: TupleValue): Boolean


    /**
     * Continue running where we left off (possibly from beginning)
     * @param control might have LogicCommand Pause,
     *  where we continue to run as little as possible from where we left off
     * @param graphDefinition
     */
    fun continueOrStart(
        control: LogicControl,
        graphDefinition: GraphDefinition
    ): LogicResult


//    /**
//     * Continue running as little as possible from where we left off (paused state or beginning),
//     *  and then pause (again)
//     */
//    fun continueOrStartStep(control: LogicControl): LogicResult {
//        return continueOrStart(control)
//    }


    fun close(error: Boolean)
}