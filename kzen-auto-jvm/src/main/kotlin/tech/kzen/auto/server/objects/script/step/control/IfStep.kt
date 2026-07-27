package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect
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
 * themselves (via `NestedList`).
 */
@Reflect
class IfStep(
    private val branches: List<ObjectLocation>,
    private val `else`: List<ObjectLocation>
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    // Read the booleans predecessor steps produced and run the first matching branch inline (on this same node,
    // through the framework spine — not a hosted child, so the frame tree is unchanged). The branch's last step
    // value is the If's own value, recorded + traced by the enclosing sequence.
    //
    // First-true-wins is LAZY: conditions after the taken branch are never read, matching if/else-if semantics.
    override suspend fun run(execution: StepExecution): Any? {
        val graphNotation = execution.graphNotation

        for (branch in branches) {
            val condition = conditionLocation(branch, graphNotation)
                ?: error("If branch condition is not set: $branch")

            val conditionValue = execution.referencedValue(condition) as? Boolean
                ?: error("If condition is not a boolean: $condition")

            if (conditionValue) {
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


    // The step whose boolean value the branch tests, or null when the branch's condition is unset / unresolvable
    // (a fresh branch defaults to ""). Resolved against the branch object, so a same-document step reference
    // resolves exactly as it does for the branch's own `condition` attribute definition.
    private fun conditionLocation(branch: ObjectLocation, graphNotation: GraphNotation): ObjectLocation? {
        val value = graphNotation
            .firstAttribute(branch, AttributePath.ofName(ScriptConventions.conditionAttributeName))
            ?.asString()

        if (value.isNullOrEmpty()) {
            return null
        }

        return graphNotation.coalesce.locateOptional(
            ObjectReference.parse(value), ObjectReferenceHost.ofLocation(branch))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The If's runtime value is whichever branch ran — i.e. that branch's terminal step value (the last
    // successful step of the branch). So statically the If's `main` type is the join of every branch's terminal
    // type (the condition branches plus the else). Returns null to defer while a branch terminal is still
    // unresolved (the validator iterates to a fixpoint).
    //
    // A branch whose condition is not configured is reported as the If's validation error and yields no type at
    // all — the same "broken until configured" signal a fresh If gave when the condition lived on the If itself
    // (it then failed to define outright). Checked before the terminal walk so a fresh If reports promptly.
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val graphNotation = scriptDefinitionContext.graphNotation

        conditionError(graphNotation)?.let {
            return ScriptStepDefinition(null, it)
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
        // otherwise Any), so folding it over N+1 terminals generalizes the former 2-way then/else join.
        // Never empty: the else terminal is always contributed, even for a hand-edited zero-branch If.
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(terminalTypes.reduce(::joinBranchTypes))))
    }


    // The first branch whose condition is unusable, named by POSITION (branch objects carry stable identity
    // names that the editor deliberately never shows, so an object name would not locate it on screen), or null
    // when every branch is configured.
    private fun conditionError(graphNotation: GraphNotation): String? {
        for ((index, branch) in branches.withIndex()) {
            if (conditionLocation(branch, graphNotation) == null) {
                return "Branch ${index + 1}: condition not set"
            }
        }
        return null
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
