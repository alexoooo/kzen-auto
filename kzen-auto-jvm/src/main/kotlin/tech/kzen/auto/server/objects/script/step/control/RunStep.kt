package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.common.objects.document.script.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.script.ScriptConventions
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
        // A RunStep yields whatever its linked logic returns. For a sub-Script the output type is declared:
        // its `results` signature (void when none is declared), so surface that rather than a blanket Any.
        // For any other linked logic (e.g. a Flow) the output type isn't declared anywhere we can read, so
        // fall back to Any instead of mislabelling it void.
        val graphNotation = scriptDefinitionContext.graphNotation
        val instructionsDocument = graphNotation.documents[instructions.documentPath]

        val returnSignature =
            if (instructionsDocument != null && ScriptConventions.isScript(instructionsDocument)) {
                ResultSignatureDefiner.parse(
                    graphNotation.firstAttribute(instructions, ScriptConventions.resultsAttributePath))
            }
            else {
                TupleDefinition.ofMain(LogicType.any)
            }

        return ScriptStepDefinition.of(returnSignature)
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
            // Track the frame boundary so Step Over / Step Out can run a frame (and its descendants) to
            // completion by depth (see LogicControl.runningFreeByDepth): while the child runs, depth is one
            // deeper, so a Step Over (limit = this step's depth) or Step Out (limit = caller depth) lets the
            // child's fresh boundaries run free; the parent's post-return work happens back at its own depth.
            val runResult = run {
                logicControl.enterFrame()
                try {
                    execution.continueOrStart(scriptExecutionContext.graphDefinition)
                }
                finally {
                    logicControl.exitFrame()
                }
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