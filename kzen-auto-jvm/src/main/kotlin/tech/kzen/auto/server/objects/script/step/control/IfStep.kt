package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.step.eval.StepExpressionSupport
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassNames


/**
 * An if / else-if / ... / else chain: the [branches] are evaluated in order and the FIRST whose condition holds
 * runs its steps; if none holds, [else] runs.
 *
 * Each branch is a nested `IfBranch` notation object owning its own condition and steps — not an index into a
 * list attribute — so branch identity is a stable object name while branch ORDER is document position (the same
 * rule steps follow). A branch is therefore added / removed / reordered by ordinary object commands, renaming
 * nothing: stable ids, breakpoints and expand state survive a reorder.
 *
 * A branch's condition and steps are read from NOTATION rather than injected, because they live one nesting
 * level down (`If.branches/<Branch>.condition` / `.steps`) and the constructor only receives the branch objects
 * themselves (via `NestedList`). That is also why the If — not `IfBranch`, which is never executed — owns
 * compiling and evaluating every branch condition.
 *
 * A condition is a user-supplied Kotlin expression compiled with a forced `Boolean` return, exactly like
 * [DoWhileStep]'s, over the values in scope AT the If (its predecessors plus the in-scope parameters / loop
 * items). It is NOT `scope: body`: a branch condition is tested before its branch runs, so the branch's own
 * steps are not in scope — the default [ScriptTree.inScopeReferencePaths] scoping applies, resolved against
 * the BRANCH object (whose sibling branches [ScriptTree.predecessors] already excludes).
 *
 * NB: like every expression step, a condition resolves EVERY in-scope value at run time, not only the ones its
 * text names (see [StepExecution.isValueReferenced]) — so an If whose predecessor was skipped by control flow
 * reaches the `referencedValue` "No value produced" backstop.
 */
@Reflect
class IfStep(
    private val branches: List<ObjectLocation>,
    private val `else`: List<ObjectLocation>,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val conditionAttributePath =
            AttributePath.ofName(ScriptConventions.conditionAttributeName)

        // A branch condition is REQUIRED to be Boolean, so the generated `evaluate` returns exactly that and a
        // non-Boolean expression is a Kotlin compile error surfaced as the If's validation error — the same
        // forcing DoWhileStep applies to its loop condition (no inference, unlike FormulaStep).
        private const val booleanReturnType = "Boolean"
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Evaluate the branch conditions and run the first matching branch inline (on this same node, through the
    // framework spine — not a hosted child, so the frame tree is unchanged). The branch's last step value is
    // the If's own value, recorded + traced by the enclosing sequence.
    //
    // First-true-wins is LAZY: conditions after the taken branch are never evaluated, matching if/else-if
    // semantics (and sparing their in-scope value resolution).
    override suspend fun run(execution: StepExecution): Any? {
        val graphNotation = execution.graphNotation

        for (branch in branches) {
            if (evaluateCondition(branch, graphNotation, execution)) {
                return execution.runSteps(branchSteps(branch, graphNotation))
            }
        }

        return execution.runSteps(`else`)
    }


    // A branch runs at most once, so these ids are exposed only so an ENCLOSING loop that re-runs drops this
    // whole sub-tree from the replay set (the If itself keeps replay on: the conditions re-evaluate
    // deterministically to the same branch, whose completed steps short-circuit).
    override fun nestedStepLists(graphNotation: GraphNotation): List<List<ObjectLocation>> {
        return branches.map { branchSteps(it, graphNotation) } + listOf(`else`)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The steps of one branch, in document order — the same list NestedListAttributeDefiner would inject for the
    // branch object's own `steps` attribute (which is what the spine's replay walk and the type join both need).
    private fun branchSteps(branch: ObjectLocation, graphNotation: GraphNotation): List<ObjectLocation> {
        return ScriptConventions.orderedDirectChildLocations(
            graphNotation, AttributeLocation(branch, ScriptConventions.stepsAttributePath))
    }


    // The branch's Kotlin condition source, blank when unset (a fresh branch defaults to "").
    private fun conditionCode(branch: ObjectLocation, graphNotation: GraphNotation): String {
        return graphNotation
            .firstAttribute(branch, conditionAttributePath)
            ?.asString()
            ?: ""
    }


    // The values the branch condition can reference by name: the If's own predecessors plus the parameters /
    // enclosing loop items in scope. Resolved against the BRANCH — ScriptTree.predecessors sees through the
    // group branch to the If's position, and excludes the sibling branches (an earlier branch did not run when
    // a later one is being tested).
    private fun conditionScopeTypes(
        branch: ObjectLocation,
        scriptTree: ScriptTree,
        scriptValidation: ScriptValidation
    ): Map<ObjectPath, TypeMetadata?> {
        return StepExpressionSupport.inScopeTypes(branch, scriptTree, scriptValidation)
    }


    private fun evaluateCondition(
        branch: ObjectLocation,
        graphNotation: GraphNotation,
        execution: StepExecution
    ): Boolean {
        val code = conditionCode(branch, graphNotation)
        check(code.isNotBlank()) {
            "If branch condition is not set: $branch"
        }

        val scope = StepExpressionSupport.resolveNonUnit(
            conditionScopeTypes(branch, execution.scriptTree, execution.scriptValidation))
            ?: error("Unresolved in-scope types for: $branch")

        val value = StepExpressionSupport.evaluate(
            branch, booleanReturnType, code, scope,
            { execution.referencedValue(it) }, cachedKotlinCompiler,
            instanceCache = { signature, factory -> execution.perRunSingleton(signature, factory) })

        return value as? Boolean
            ?: error("If branch condition is not a boolean: $branch")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The If's runtime value is whichever branch ran — i.e. that branch's terminal step value (the last
    // successful step of the branch). So statically the If's `main` type is the join of every branch's terminal
    // type (the condition branches plus the else). Returns null to defer while a branch terminal is still
    // unresolved (the validator iterates to a fixpoint).
    //
    // A branch whose condition is not configured or does not compile is reported as the If's validation error
    // and yields no type at all, so the whole construct reads as broken until every branch is configured.
    // Checked before the terminal walk so a fresh If reports promptly.
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val graphNotation = scriptDefinitionContext.graphNotation

        // Every condition compiles (or is reported / deferred on) before any type work: a fresh If must report
        // its unconfigured branch promptly, and a broken condition makes the whole If broken regardless of the
        // branch terminals.
        for ((index, branch) in branches.withIndex()) {
            val code = conditionCode(branch, graphNotation)

            // The blank case is called out rather than left to the compiler: an empty expression compiles to a
            // `Unit` body, whose "Boolean expected" error says nothing about what the user must actually do.
            if (code.isBlank()) {
                return ScriptStepDefinition(null, "Branch ${index + 1}: condition not set")
            }

            val scope = StepExpressionSupport.resolveNonUnit(
                conditionScopeTypes(
                    branch,
                    scriptDefinitionContext.scriptTree,
                    scriptDefinitionContext.scriptValidation))
                ?: return null

            // Generated through StepExpressionSupport (not a locally-named class) so validation and execution
            // share one content signature — the condition compiles once and `evaluate` reuses the artifact.
            val generatedCode = StepExpressionSupport.generateCode(branch, booleanReturnType, code, scope)
            val compileError = cachedKotlinCompiler.tryCompile(
                generatedCode, ClassLoaderUtils.dynamicParentClassLoader())

            if (compileError != null) {
                // No offset: the condition belongs to the BRANCH's own editor, and this definition is the
                // If step's — a position here would point into a different object's text.
                return ScriptStepDefinition(null, "Branch ${index + 1}: ${compileError.error}")
            }
        }

        val terminalTypes = mutableListOf<TypeMetadata>()
        for (branch in branches) {
            terminalTypes.add(
                branchTerminalType(branchSteps(branch, graphNotation), scriptDefinitionContext)
                    ?: return null)
        }
        terminalTypes.add(
            branchTerminalType(`else`, scriptDefinitionContext)
                ?: return null)

        // joinBranchTypes is associative under its own semantics (Unit dominates; equal shape ORs nullability;
        // otherwise Any), so it folds over any number of terminals. Never empty: the else terminal is always
        // contributed, even for a hand-edited zero-branch If.
        return ScriptStepDefinition.ofMain(terminalTypes.reduce(::joinBranchTypes))
    }


    // The type the branch contributes as the If's value: its last step's resolved type, or Unit when the
    // branch is empty (no value) or its terminal validated without a type (e.g. a compile error). Null means
    // the terminal isn't validated yet — the caller should defer.
    private fun branchTerminalType(
        branch: List<ObjectLocation>,
        scriptDefinitionContext: ScriptDefinitionContext
    ): TypeMetadata? {
        val terminal = branch.lastOrNull()
            ?: return TypeMetadata.unit

        val validation = scriptDefinitionContext.scriptValidation.stepValidations[terminal.objectPath]
            ?: return null

        return validation.typeMetadata ?: TypeMetadata.unit
    }


    // Least common type of two branches: identical shape => that type (nullable if either is); a valueless
    // (Unit) branch => Unit (the If doesn't dependably yield a value); otherwise widen to the only guaranteed
    // common supertype, Any. Conservative by design — uniform branch types give a precise, referenceable type.
    private fun joinBranchTypes(a: TypeMetadata, b: TypeMetadata): TypeMetadata {
        if (a.className == ClassNames.kotlinUnit || b.className == ClassNames.kotlinUnit) {
            return TypeMetadata.unit
        }
        if (a.className == b.className && a.generics == b.generics) {
            return TypeMetadata(a.className, a.generics, a.nullable || b.nullable)
        }
        return TypeMetadata(ClassNames.kotlinAny, listOf(), a.nullable || b.nullable)
    }
}
