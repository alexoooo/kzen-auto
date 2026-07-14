package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.step.eval.StepExpressionCompiler
import tech.kzen.auto.server.objects.script.step.eval.StepExpressionSupport
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * A do-while loop: runs its body branch, then evaluates a user-supplied Kotlin [condition] (compiled like
 * [tech.kzen.auto.server.objects.script.step.eval.FormulaStep]'s formula, but *required* to be Boolean — so
 * no type inference); while the condition is true the body repeats. The condition can reference the body
 * steps' just-produced values by name (plus any in-scope parameters / enclosing loop items).
 */
@Reflect
class DoWhileStep(
    private val condition: String,
    steps: List<ObjectLocation>,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    private val bodySteps = steps


    //-----------------------------------------------------------------------------------------------------------------
    // MID-LOOP MIGRATION RESUME (logic-spec §5): reaching here means the loop did NOT complete pre-edit; a
    // coroutine's do/while can't be re-pointed at the rebuilt body, so it re-enters from the top and resumes
    // from its restored carry (the completed-iteration count, a mid-flight marker): the in-flight iteration's
    // completed body prefix replays to the frontier, then the condition evaluates against the refreshed values —
    // no completed iteration re-runs (unlike ForEach there are no per-iteration outputs to seed, and no
    // iterations to skip: prior iterations left no state the loop needs). With no carry the loop starts fresh
    // (see ForEachStep for the full cursor rationale).
    override suspend fun run(execution: StepExecution): Any? {
        val restored = execution.restoredCarry(selfLocation) as? Int
        var iterations = restored ?: 0

        // Re-record at entry: the rebuilt run's carry starts empty, so a second edit before the next iteration
        // completes must still capture the mid-flight marker (also covers a pause during the first iteration).
        execution.recordCarry(selfLocation, iterations)

        var replayInFlight = restored != null
        do {
            // Iteration reset (see [StepExecution.dropReplay]) — skipped for a resumed in-flight iteration,
            // whose completed body prefix must stay replayable.
            if (!replayInFlight) {
                execution.dropReplay(bodySteps)
            }
            replayInFlight = false

            execution.runSteps(bodySteps)

            iterations += 1
            execution.recordCarry(selfLocation, iterations)
        }
        while (evaluateCondition(execution))

        // Completed: the loop's own outcome carries; a stale marker must not.
        execution.recordCarry(selfLocation, null)
        return null
    }


    override fun nestedStepLists(): List<List<ObjectLocation>> {
        return listOf(bodySteps)
    }


    private fun evaluateCondition(execution: StepExecution): Boolean {
        val scope = StepExpressionSupport.resolveNonUnit(
            conditionScopeTypes(execution.scriptTree, execution.scriptValidation))
            ?: error("Unresolved in-scope types for: $selfLocation")
        val value = StepExpressionSupport.evaluate(
            selfLocation, "Boolean", condition, scope,
            { execution.referencedValue(it) }, cachedKotlinCompiler,
            instanceCache = { signature, factory -> execution.perRunSingleton(signature, factory) })
        return value as? Boolean
            ?: error("Do-while condition is not a boolean: $selfLocation")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        // Defer (null) until every in-scope value's type is known — like ForEachStep defers on `items`.
        // The ScriptValidator iterates to a fixpoint, so a later pass resolves the body step types.
        val scope = StepExpressionSupport.resolveNonUnit(
            conditionScopeTypes(scriptDefinitionContext.scriptTree, scriptDefinitionContext.scriptValidation))
            ?: return null

        val code = generateConditionCode(scope)
        val error = cachedKotlinCompiler.tryCompile(code, ClassLoaderUtils.dynamicParentClassLoader())
        if (error != null) {
            return ScriptStepDefinition(null, error)
        }

        return ScriptStepDefinition.of(TupleDefinition.empty)
    }


    private fun generateConditionCode(scope: Map<ObjectPath, TypeMetadata>) =
        StepExpressionCompiler.generateCode(
            "Cond_" + StepExpressionCompiler.sanitizeName(selfLocation.objectPath.name.value),
            "Boolean",
            condition,
            scope)


    /**
     * The values the condition can reference by name: the body steps (this loop's children) plus the
     * parameters / enclosing loop items in scope. Body steps are children — not predecessors — so they're
     * taken from the constructor's `steps` list, not [ScriptTree.predecessors].
     */
    private fun conditionScopeTypes(
        scriptTree: ScriptTree,
        scriptValidation: ScriptValidation
    ): Map<ObjectPath, TypeMetadata?> {
        val bindings = scriptTree.inScopeBindingPaths(selfLocation.objectPath)
        return StepExpressionSupport.typesOf(bodySteps.map { it.objectPath } + bindings, scriptValidation)
    }
}
