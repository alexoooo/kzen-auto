package tech.kzen.auto.server.objects.script.step.control.foreach

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


@Reflect
class ForEachStep(
    @Suppress("unused") private val items: ObjectLocation,
    steps: List<ObjectLocation>,
    @Suppress("unused") private val selfLocation: ObjectLocation
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    private val bodySteps = steps


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        // Output is a List of what the loop collects each iteration: the body's terminal step value,
        // mirroring IfStep whose type is its branches' terminal type. NOT the `items` element type — that's
        // the loop variable's type (ForEachItemBinding), which it reads straight from `items`. An empty body
        // has no value to collect, so its element type is unknown (Any). Defer (null) until the terminal step
        // is validated; ScriptValidator iterates to a fixpoint.
        val elementType = bodyTerminalType(scriptDefinitionContext)
            ?: return null

        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(
                TypeMetadata(
                    ClassNames.kotlinList,
                    listOf(elementType),
                    false))))
    }


    // The element type the loop collects: its last body step's resolved type, Unit when that terminal
    // validated without a value, or Any for an empty body. Null means the terminal isn't validated yet —
    // the caller should defer.
    private fun bodyTerminalType(scriptDefinitionContext: ScriptDefinitionContext): TypeMetadata? {
        val terminal = bodySteps.lastOrNull()
            ?: return TypeMetadata.any

        val validation = scriptDefinitionContext.scriptValidation.stepValidations[terminal.objectPath]
            ?: return null

        return validation.typeMetadata ?: TypeMetadata.unit
    }
}
