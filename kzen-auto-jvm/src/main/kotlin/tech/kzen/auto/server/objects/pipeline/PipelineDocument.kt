package tech.kzen.auto.server.objects.pipeline

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.server.service.v1.Logic
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicExecution
import tech.kzen.auto.server.service.v1.LogicHandle
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class PipelineDocument(
    private val selfLocation: ObjectLocation
):
    DocumentArchetype(),
    Logic
{
    //-----------------------------------------------------------------------------------------------------------------
    private class Execution: LogicExecution {
        override fun next(arguments: TupleValue): LogicResult {
            return LogicResultSuccess(TupleValue.ofMain("foo"))
        }

        override fun run(control: LogicControl): LogicResult {
            throw IllegalStateException()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(): LogicDefinition {
        return LogicDefinition(
            TupleDefinition.empty,
            TupleDefinition.ofMain(LogicType.string))
    }


    override fun execute(handle: LogicHandle): LogicExecution {
        return Execution()
    }
}