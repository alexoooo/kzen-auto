package tech.kzen.auto.server.objects.script.step.control.foreach

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


@Reflect
class ForEachStep(
    private val items: ObjectLocation,
    steps: List<ObjectLocation>,
    private val selfLocation: ObjectLocation
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    private val bodySteps = steps


    //-----------------------------------------------------------------------------------------------------------------
    // Loop over the iterable a predecessor produced, running the body once per element and collecting each
    // iteration's terminal value. The current element is bound under the loop's `item` binding so body
    // expressions can reference it. The iterator lives on the coroutine stack (an ordinary `for`).
    override suspend fun run(execution: StepExecution): Any? {
        val iterable = execution.referencedValue(items) as? Iterable<*>
            ?: error("ForEach items are not iterable: $items")

        val itemBinding = ScriptConventions.orderedDirectChildLocations(
            execution.graphNotation, AttributeLocation(selfLocation, ScriptConventions.itemAttributePath))
            .singleOrNull()
            ?: error("ForEach step has no item binding: $selfLocation")

        // Reaching here means the loop did NOT complete pre-edit (a completed loop carries its own outcome and is
        // short-circuited wholesale by the enclosing sequence). A coroutine's `for` can't be re-pointed at the
        // rebuilt body, so the loop restarts from its first iteration — drop the body's stale per-iteration
        // outcomes from the replay set so each body step executes live instead of short-circuiting on them.
        execution.dropReplay(bodySteps)

        val output = ArrayList<Any?>()
        for (item in iterable) {
            execution.bind(itemBinding, item)
            output.add(execution.runSteps(bodySteps))
        }
        return output
    }


    override fun nestedStepLists(): List<List<ObjectLocation>> {
        return listOf(bodySteps)
    }


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
