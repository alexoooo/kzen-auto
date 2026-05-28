package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.v1.LogicExecutionFacade
import tech.kzen.auto.server.service.v1.StatefulLogicElement
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentName
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentValue
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.ExceptionUtils


@Reflect
class RunStep(
    private val instructions: ObjectLocation,
    private val arguments: Map<String, ObjectLocation>,
    selfLocation: ObjectLocation
):
    TracingScriptStep(selfLocation),
    StatefulLogicElement<RunStep>
{
//    companion object {
//        private val logger = LoggerFactory.getLogger(InvokeScriptStep::class.java)
//    }


    private var pausedExecution: LogicExecutionFacade? = null


    override fun loadState(previous: RunStep) {
        pausedExecution = previous.pausedExecution
    }


    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.any))
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        val command = scriptExecutionContext.logicControl.pollCommand()
        if (command == LogicCommand.Cancel) {
            pausedExecution?.close()
            pausedExecution = null
            return LogicResultCancelled
        }

        val existing = pausedExecution

        val execution =
            if (existing != null) {
                existing
            }
            else {
                val created = scriptExecutionContext.logicHandleFacade.start(instructions)

                val argumentTupleComponents = arguments.map {
                    TupleComponentValue(
                        TupleComponentName(it.key),
                        scriptExecutionContext.stepModel(it.value)?.value?.mainComponentValue())
                }

                val argumentValue = TupleValue(argumentTupleComponents)

                val initResult = created.beforeStart(argumentValue)
                if (!initResult) {
                    created.close()
                    return LogicResultFailed("Unable to initialize $instructions")
                }

                created
            }

        try {
            val runResult = execution.continueOrStart()

            pausedExecution =
                if (runResult is LogicResultPaused) {
                    execution
                }
                else {
                    if (runResult is LogicResultSuccess) {
                        traceValue(scriptExecutionContext, runResult.value.mainComponentValue())
                    }

                    execution.close()
                    null
                }

            return runResult
        }
        catch (t: Throwable) {
            t.printStackTrace()
            execution.close()
            pausedExecution = null
            return LogicResultFailed(
                ExceptionUtils.message(t))
        }
    }
}