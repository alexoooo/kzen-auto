package tech.kzen.auto.server.objects.custom.test

import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunExecutionId
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.service.v1.Logic
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.LogicHandle
import tech.kzen.auto.server.service.v1.model.LogicDefinition
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentName
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class AdhocLogic: Logic {
    //-----------------------------------------------------------------------------------------------------------------
    fun hello(): String {
        println("foo")
        return "Hello World"
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(): LogicDefinition {
        val outputs = listOf(
            TupleComponentDefinition(TupleComponentName.main, LogicType.string))
        return LogicDefinition(
            TupleDefinition.empty,
            TupleDefinition(outputs))
    }


    override fun execute(
        logicHandle: LogicHandle,
        logicTraceHandle: LogicTraceHandle,
        logicRunExecutionId: LogicRunExecutionId,
        logicControl: LogicControl
    ): LogicExecution {
        return Execution()
    }


    private inner class Execution: LogicExecution {
        override fun beforeStart(arguments: TupleValue): Boolean {
            return true
        }

        override fun continueOrStart(
            logicControl: LogicControl,
            graphDefinition: GraphDefinition
        ): LogicResult {
            val result = hello()
            return LogicResultSuccess(TupleValue.ofMain(result))
        }

        override fun close(error: Boolean) {}
    }
}