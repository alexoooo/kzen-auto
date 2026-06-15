package tech.kzen.auto.server.objects.custom.test

import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.LogicResourceScope
import tech.kzen.lib.common.exec.logic.model.LogicDefinition
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class AdhocLogic(
    private val named: AdhocNamed
): Logic {
    //-----------------------------------------------------------------------------------------------------------------
    fun hello(): String {
        val name = named.name()
        return "Hello, $name!"
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
            resourceScope: LogicResourceScope,
            graphDefinition: GraphDefinition
        ): LogicResult {
            val result = hello()
            return LogicResultSuccess(TupleValue.ofMain(result))
        }

        override fun close(error: Boolean) {}
    }
}