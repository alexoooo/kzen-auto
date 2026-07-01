package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


@Reflect
class IfStep(
    private val condition: ObjectLocation,
    private val then: List<ObjectLocation>,
    private val `else`: List<ObjectLocation>
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    // Read the boolean a predecessor step produced and run one branch inline (on this same node, through the
    // framework spine — not a hosted child, so the frame tree is unchanged). The branch's last step value is the
    // If's own value, recorded + traced by the enclosing sequence.
    override suspend fun run(execution: StepExecution): Any? {
        val conditionValue = execution.referencedValue(condition) as? Boolean
            ?: error("If condition is not a boolean: $condition")

        return execution.runSteps(if (conditionValue) then else `else`)
    }


    // A branch runs at most once, so these ids are exposed only so an ENCLOSING loop that re-runs drops this
    // whole sub-tree from the replay set (the If itself keeps replay on: the condition re-evaluates
    // deterministically to the same branch, whose completed steps short-circuit).
    override fun nestedStepLists(): List<List<ObjectLocation>> {
        return listOf(then, `else`)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The If's runtime value is whichever branch ran — i.e. that branch's terminal step value (the last
    // successful step of the branch). So statically the If's `main` type is the join of the two branches'
    // terminal types, making the If referenceable by name from a downstream expression. Returns null to defer
    // while a branch terminal is still unresolved (the validator iterates to a fixpoint).
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val thenType = branchTerminalType(then, scriptDefinitionContext)
            ?: return null
        val elseType = branchTerminalType(`else`, scriptDefinitionContext)
            ?: return null

        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(joinBranchTypes(thenType, elseType))))
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


    // Least common type of the two branches: identical shape => that type (nullable if either is); a valueless
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
