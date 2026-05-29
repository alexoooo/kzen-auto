package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.objects.script.step.control.MultiStep
import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicDefinition
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class ScriptDocument(
    steps: List<ObjectLocation>,
    private val parameters: List<String>,
    private val results: List<String>,
    private val selfLocation: ObjectLocation
):
    DocumentArchetype(),
    Logic,
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun define(): LogicDefinition {
        val inputs = parameters.map {
            TupleComponentDefinition(TupleComponentName(it), LogicType.any)
        }

        val outputs = results.map {
            TupleComponentDefinition(TupleComponentName(it), LogicType.any)
        }

        return LogicDefinition(
            TupleDefinition(inputs),
            TupleDefinition(outputs))
    }


    override fun execute(
        logicHandle: LogicHandle,
        logicTraceHandle: LogicTraceHandle,
        logicRunExecutionId: LogicRunExecutionId,
        logicControl: LogicControl
    ): LogicExecution {
        val scriptExecution = ScriptExecution(
            selfLocation.documentPath, selfLocation,
            logicHandle, logicTraceHandle, logicRunExecutionId,
            KzenAutoContext.global().objectStableMapper)
        scriptExecution.init(logicControl)
        return scriptExecution
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val scriptStepDelegate = MultiStep(steps)


    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return scriptStepDelegate.definition(scriptDefinitionContext)
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        return scriptStepDelegate.continueOrStart(scriptExecutionContext)
    }
}