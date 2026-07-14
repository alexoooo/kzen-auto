package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.common.objects.document.script.model.ScriptNestingAnalysis
import tech.kzen.auto.server.objects.script.api.ScriptControlSignal
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Structured loop control (`continue` / `break`): raises a [ScriptControlSignal] targeting an enclosing loop so
 * the loop skips the rest of this iteration ([action] `skipIteration`) or exits ([action] `finishLoop`). Produces
 * no value. The transfer is a completion signal, not an exception (see [ScriptControlSignal]); a mistargeted
 * [loop] — one that is not a `rerun`-flagged enclosing ancestor of this step — is a validation error that blocks
 * Run, so the runtime backstop (a signal reaching the Script root unconsumed) should be unreachable.
 */
@Reflect
class ControlStep(
    private val loop: ObjectLocation,
    private val action: String,
    private val selfLocation: ObjectLocation
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val skipIteration = "skipIteration"
        const val finishLoop = "finishLoop"
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        if (action != skipIteration && action != finishLoop) {
            return ScriptStepDefinition(null, "Invalid loop control action: $action")
        }

        val enclosingLoops = ScriptNestingAnalysis.enclosingLoops(
            scriptDefinitionContext.graphNotation,
            selfLocation.documentPath,
            scriptDefinitionContext.scriptTree,
            selfLocation.objectPath)

        if (loop !in enclosingLoops) {
            return ScriptStepDefinition(
                null, "Loop control target is not an enclosing loop: ${loop.objectPath.name.value}")
        }

        return ScriptStepDefinition.empty
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun run(execution: StepExecution): Any? {
        val signal = when (action) {
            skipIteration -> ScriptControlSignal.SkipIteration(loop)
            finishLoop -> ScriptControlSignal.FinishLoop(loop)
            else -> error("Invalid loop control action: $action")
        }
        execution.traceDetail(if (action == skipIteration) "Skip iteration" else "Finish loop")
        execution.raiseControlSignal(signal)
        return null
    }
}
