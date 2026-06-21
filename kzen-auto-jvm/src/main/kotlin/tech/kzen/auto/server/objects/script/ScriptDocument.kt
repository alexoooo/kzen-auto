package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.objects.document.DocumentArchetype
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
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


@Reflect
class ScriptDocument(
    steps: List<ObjectLocation>,
    parameters: List<ObjectLocation>,
    private val results: List<String>,
    private val selfLocation: ObjectLocation,

    @Service private val objectStableMapper: ObjectStableMapper,
    @Service private val graphCreator: GraphCreator,
    @Service private val environment: GraphEnvironment
):
    DocumentArchetype(),
    Logic,
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    // Each parameter is a ParameterBinding object in the `parameters` branch; its name is the object's
    // own name. The declared per-parameter TypeMetadata powers in-script typing via each binding's own
    // definition() (-> ScriptValidation -> FormulaStep); the external Logic signature stays `any` for now
    // since nothing consumes LogicDefinition.inputs yet (caller-side argument type-checking is future work).
    private val parameterNames = parameters.map { it.objectPath.name.value }


    override fun define(): LogicDefinition {
        val inputs = parameterNames.map {
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
        return ScriptExecution(
            selfLocation.documentPath, selfLocation,
            logicHandle, logicTraceHandle, logicRunExecutionId,
            objectStableMapper,
            graphCreator,
            environment)
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