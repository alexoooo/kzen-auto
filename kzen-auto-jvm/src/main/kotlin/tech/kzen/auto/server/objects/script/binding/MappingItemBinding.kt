package tech.kzen.auto.server.objects.script.binding

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.ScriptValueBinding
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.objects.script.step.control.mapping.MappingStep
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
 * Exposes a MappingStep's current loop item to nested steps by name, fully typed, without a body row
 * (replaces the old MappingItemStep). Lives in the enclosing mapping's `item` branch, so it is validated
 * and referenceable like a step but never executed. Its type is the element type of the mapping's List
 * output (itself inferred from the items collection); its value is the mapping's current `next` item,
 * resolved on demand.
 */
@Reflect
class MappingItemBinding(
    private val selfLocation: ObjectLocation
):
    ScriptStep,
    ScriptValueBinding
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val mappingLocation = selfLocation.parent()
            ?: return ScriptStepDefinition.of(TupleDefinition.ofMain(LogicType.anyNullable))

        // Element type of the enclosing mapping's List<X> output. Defer (null) until the mapping has been
        // validated so the element type can refine past Any (ScriptValidator iterates to a fixpoint).
        val mappingType = scriptDefinitionContext.scriptValidation
            .stepValidations[mappingLocation.objectPath]?.typeMetadata
            ?: return null

        val elementType = mappingType.generics.firstOrNull() ?: TypeMetadata.any

        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(elementType)))
    }


    override fun resolveValue(scriptExecutionContext: ScriptExecutionContext): Any? {
        return enclosingMapping(scriptExecutionContext)?.next
    }


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        val mapping = enclosingMapping(scriptExecutionContext)
            ?: return LogicResultFailed("Enclosing mapping not found: $selfLocation")
        return LogicResultSuccess(
            TupleValue.ofMain(mapping.next))
    }


    private fun enclosingMapping(scriptExecutionContext: ScriptExecutionContext): MappingStep? {
        val mappingLocation = selfLocation.parent()
            ?: return null
        return scriptExecutionContext.graphInstance[mappingLocation]?.reference as? MappingStep
    }
}
