package tech.kzen.auto.server.exec.script

import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A Script as a [Logic]: run the root step sequence to completion and return the captured result, or void when
 * no [ResultStep][tech.kzen.auto.server.objects.script.step.eval.ResultStep] ran (there is no last-step
 * fallback — matching the established Script result contract).
 *
 * The steps themselves are the `@Reflect` notation archetypes resolved from [ScriptRunStructure.graphInstance];
 * this class only seeds the parameters and the live-edit carry-over, then drives the spine
 * ([ScriptRunContext.runSteps]) over the root step locations. No step types are enumerated here — adding a step
 * type (Browser, or a third-party step) requires no change to this class or the compiler.
 */
class ScriptLogic(
    private val rootStepLocations: List<ObjectLocation>,
    private val parameters: List<ScriptParameter>,
    private val structure: ScriptRunStructure,
    private val logicSignature: LogicSignature
): Logic {
    override fun signature(): LogicSignature {
        return logicSignature
    }


    override suspend fun run(execution: Execution): TupleValue {
        val context = ScriptRunContext(execution, structure)

        // Live-edit migration (logic-spec §5): adopt the predecessor run's completed work (read once at start) so
        // the spine replays-short-circuits completed steps, and register the capture so a later edit carries this
        // run's completed work forward. Null on a fresh run -> nothing to adopt, everything runs live.
        (execution.restored as? ScriptMigrationState)?.let { context.restore(it) }
        execution.onCapture { context.captureState() }

        for (parameter in parameters) {
            context.recordValue(
                parameter.stableId,
                execution.inputs.find(parameter.name) ?: parameter.default)
        }

        context.runSteps(rootStepLocations)

        // Control flow: an End Script signal (return) that unwound to the root is consumed here; a Skip / Finish
        // reaching the root is a mistargeted control step and fails the run (validation should prevent it).
        context.consumeRootSignalOrFail()

        return context.result()
            ?: TupleValue.empty
    }
}
