package tech.kzen.auto.server.objects.script.step.control

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.lib.common.exec.logic.model.*
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.ExceptionUtils


@Reflect
class MultiStep(
    private val steps: List<ObjectLocation>
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(MultiStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.empty)
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        var lastSuccessValue: TupleValue = TupleValue.empty

        val logicControl = scriptExecutionContext.logicControl

        while (true) {
            val nextToRun = getAndPublishNextToRun(scriptExecutionContext)
                ?: return LogicResultSuccess(lastSuccessValue)

            val stepModel = scriptExecutionContext.getOrPutStepModel(nextToRun)

            // Cancel always wins. Otherwise: a step already Running paused mid-execution (its nested
            // child paused) — it's on the resume spine, so re-enter it to resume regardless of any
            // pause/budget. A fresh step (Idle, or Error under pause-on-error) is a pausable boundary:
            // while a Pause command is in effect it runs only if the shared per-tick step budget allows
            // it (so a single Step / slow-motion tick advances exactly one fresh boundary across the
            // whole frame tree), unless pause is suppressed (Step Over running a sub-tree to completion).
            // nextStepTracePath was just published, so a fresh step that pauses shows as "next to run".
            val logicCommand = logicControl.pollCommand()
            if (logicCommand == LogicCommand.Cancel) {
                return LogicResultCancelled
            }

            val onResumeSpine = stepModel.traceState == StepTrace.State.Running
            if (! onResumeSpine &&
                    logicCommand == LogicCommand.Pause &&
                    ! logicControl.suppressPause() &&
                    ! logicControl.consumeStepBudget()
            ) {
                return LogicResultPaused
            }

            val step = scriptExecutionContext.graphInstance[nextToRun]?.reference as? ScriptStep
                ?: throw IllegalStateException("Next step not found: $nextToRun")

            val logicTracePath = LogicTracePath.ofObjectStableId(
                scriptExecutionContext.objectStableMapper.objectStableId(nextToRun))
            stepModel.traceState = StepTrace.State.Running
            scriptExecutionContext.logicTraceHandle.set(
                logicTracePath,
                stepModel.trace().asExecutionValue())

            @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
            val result =
                try {
                    step.continueOrStart(scriptExecutionContext)
                }
                catch (t: Throwable) {
                    logger.warn("Step error - {}", nextToRun, t)
                    LogicResultFailed(ExceptionUtils.message(t))
                }

            when (result) {
                is LogicResultSuccess -> {
                    stepModel.value = result.value
                    stepModel.error = null
                    stepModel.traceState = StepTrace.State.Done
                    scriptExecutionContext.logicTraceHandle.set(
                        logicTracePath,
                        stepModel.trace().asExecutionValue())
                    lastSuccessValue = result.value
                }

                is LogicResultFailed -> {
                    stepModel.value = null
                    stepModel.error = result.message

                    if (scriptExecutionContext.logicControl.pauseOnError()) {
                        // Pause at the failed step instead of ending the run. The Error state keeps
                        // it as "next to run" (nextToRun does NOT skip Error), so once the user
                        // fixes the step and resumes, the existing pause path re-runs just it. Leave
                        // nextStepTracePath pointing here so the client highlights it.
                        stepModel.traceState = StepTrace.State.Error
                        scriptExecutionContext.logicTraceHandle.set(
                            logicTracePath,
                            stepModel.trace().asExecutionValue())
                        return LogicResultPaused
                    }

                    stepModel.traceState = StepTrace.State.Done
                    scriptExecutionContext.logicTraceHandle.set(
                        logicTracePath,
                        stepModel.trace().asExecutionValue())
                    scriptExecutionContext.logicTraceHandle.set(
                        ScriptConventions.nextStepTracePath,
                        NullExecutionValue
                    )
                    return result
                }

                LogicResultCancelled -> {
                    stepModel.value = null
                    stepModel.error = null
                    stepModel.traceState = StepTrace.State.Done
                    scriptExecutionContext.logicTraceHandle.set(
                        logicTracePath,
                        stepModel.trace().asExecutionValue())
                    return result
                }

                LogicResultPaused -> {
                    stepModel.value = null
                    stepModel.error = null
                    stepModel.traceState = StepTrace.State.Running
                    scriptExecutionContext.logicTraceHandle.set(
                        logicTracePath,
                        stepModel.trace().asExecutionValue())
                    return result
                }
            }
        }
    }


    private fun getAndPublishNextToRun(stepContext: ScriptExecutionContext): ObjectLocation? {
        val nextToRun = nextToRun(stepContext)

        if (nextToRun == null) {
            stepContext.activeScriptModel.next = null
            stepContext.logicTraceHandle.set(
                ScriptConventions.nextStepTracePath,
                NullExecutionValue)
            return null
        }

        val nextStableId = stepContext.objectStableMapper.objectStableId(nextToRun)
        stepContext.activeScriptModel.next = nextStableId
        // Stable id (not the current location) so the client's "next to run" highlight survives a rename
        stepContext.logicTraceHandle.set(
            ScriptConventions.nextStepTracePath,
            ExecutionValue.of(nextStableId.value))

        return nextToRun
    }


    private fun nextToRun(stepContext: ScriptExecutionContext): ObjectLocation? {
        for (stepLocation in steps) {
            val model = stepContext.getOrPutStepModel(stepLocation)
            // Only Done is skipped. An Error step (pause-on-error) is intentionally runnable, so a
            // resume re-runs it; on success it becomes Done and is skipped thereafter.
            if (model.traceState == StepTrace.State.Done) {
                continue
            }
            return stepLocation
        }
        return null
    }
}