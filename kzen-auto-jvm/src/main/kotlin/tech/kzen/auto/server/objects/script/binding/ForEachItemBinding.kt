package tech.kzen.auto.server.objects.script.binding

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.ScriptValueBinding
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.objects.script.step.control.foreach.ForEachStep
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect


/**
 * Exposes a ForEachStep's current loop item to nested steps by name, fully typed, without a body row
 * (replaces the old ForEachItemStep). Lives in the enclosing ForEach's `item` branch, so it is validated
 * and referenceable like a step but never executed. Its type is the element type of the ForEach's List
 * output (itself inferred from the items collection); its value is the ForEach's current `next` item,
 * resolved on demand.
 */
@Reflect
class ForEachItemBinding(
    private val selfLocation: ObjectLocation
):
    ScriptStep,
    ScriptValueBinding
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val forEachLocation = selfLocation.parent()
            ?: return ScriptStepDefinition.of(TupleDefinition.ofMain(LogicType.anyNullable))

        // Element type of the enclosing ForEach's List<X> output. Defer (null) until the ForEach has been
        // validated so the element type can refine past Any (ScriptValidator iterates to a fixpoint).
        val forEachType = scriptDefinitionContext.scriptValidation
            .stepValidations[forEachLocation.objectPath]?.typeMetadata
            ?: return null

        val elementType = forEachType.generics.firstOrNull() ?: TypeMetadata.any

        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(elementType)))
    }


    override fun resolveValue(scriptExecutionContext: ScriptExecutionContext): Any? {
        return enclosingForEach(scriptExecutionContext)?.next
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        val forEach = enclosingForEach(scriptExecutionContext)
            ?: return LogicResultFailed("Enclosing ForEach not found: $selfLocation")
        return LogicResultSuccess(
            TupleValue.ofMain(forEach.next))
    }


    private fun enclosingForEach(scriptExecutionContext: ScriptExecutionContext): ForEachStep? {
        val forEachLocation = selfLocation.parent()
            ?: return null
        return scriptExecutionContext.graphInstance[forEachLocation]?.reference as? ForEachStep
    }
}
