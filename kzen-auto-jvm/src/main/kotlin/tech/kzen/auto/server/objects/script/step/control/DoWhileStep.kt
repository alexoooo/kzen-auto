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
    override suspend fun run(execution: StepExecution): Any? {
        // Reaching here means the loop did NOT complete pre-edit; a coroutine's do/while can't be re-pointed at
        // the rebuilt body, so it restarts from the first iteration — drop the body's stale per-iteration
        // outcomes from the replay set so each body step executes live (see ForEachStep for the full rationale).
        execution.dropReplay(bodySteps)

        do {
            execution.runSteps(bodySteps)
        }
        while (evaluateCondition(execution))

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
