package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.StatefulLogicElement
import tech.kzen.lib.common.exec.logic.model.*
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
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
        val logicControl = scriptExecutionContext.logicControl

        val command = logicControl.pollCommand()
        if (command == LogicCommand.Cancel) {
            pausedExecution?.close()
            pausedExecution = null
            return LogicResultCancelled
        }

        val existing = pausedExecution

        // Step Over: on a fresh descent (no paused child to resume) during a step-over tick, run the
        // child sub-document to completion instead of descending into it — the parent then pauses at its
        // next step. A non-null pausedExecution means we're on the resume spine (a normal step-into that
        // paused deeper), so resume it normally.
        val stepOverChild = logicControl.stepOverActive() && existing == null

        val execution =
            if (existing != null) {
                existing
            }
            else {
                val created = scriptExecutionContext.logicHandleFacade.start(instructions)

                val argumentTupleComponents = arguments.map {
                    TupleComponentValue(
                        TupleComponentName(it.key),
                        scriptExecutionContext.referencedValue(it.value))
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
            val runResult =
                if (stepOverChild) {
                    logicControl.pushSuppressPause()
                    try {
                        execution.continueOrStart(scriptExecutionContext.graphDefinition)
                    }
                    finally {
                        logicControl.popSuppressPause()
                    }
                }
                else {
                    execution.continueOrStart(scriptExecutionContext.graphDefinition)
                }

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