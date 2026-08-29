package tech.kzen.auto.server.objects.script.binding

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.ScriptValueBinding
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.step.control.foreach.ForEachItemsExpression
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * Exposes a ForEachStep's current loop item to nested steps by name, fully typed, without a body row
 * (replaces the old ForEachItemStep). Lives in the enclosing ForEach's `item` branch, so it is validated
 * and referenceable like a step but never executed; its value is supplied by the engine's loop at run time.
 *
 * Its type is the element type of the enclosing ForEach's `items` EXPRESSION, derived here from that
 * expression directly rather than read back from the ForEach's validation — which is not merely a style
 * choice: the ForEach's own type is the List of its BODY's terminal type, so reading it would be circular
 * whenever the body references this item, and `ScriptValidator` records a step's definition exactly once,
 * which rules out the ForEach publishing the element type alongside a deferred type of its own. See
 * [ForEachItemsExpression], which owns the derivation for both and makes the second compile a cache hit.
 */
@Reflect
class ForEachItemBinding(
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    ScriptValueBinding()
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val forEachLocation = selfLocation.parent()
            ?: return ScriptStepDefinition.ofMain(LogicType.anyNullable.metadata)

        val code = ForEachItemsExpression.code(
            forEachLocation, scriptDefinitionContext.graphNotation)

        val elementType = when (val attempt = ForEachItemsExpression.analyze(
                forEachLocation, code, scriptDefinitionContext, cachedKotlinCompiler)) {
            // The same defer the ForEach takes, and for the same reason: a step is recorded ONCE, so
            // publishing a fallback now would permanently lose the precise element type.
            ForEachItemsExpression.Attempt.Deferred ->
                return null

            // No error reported here — the ForEach owns the items editor and reports it there. The item must
            // still publish a TYPE, so the body keeps validating and showing its own problems rather than a
            // cascade of "Unresolved: circular or unavailable dependency".
            is ForEachItemsExpression.Attempt.Invalid ->
                TypeMetadata.anyNullable

            is ForEachItemsExpression.Attempt.Valid ->
                attempt.elementType
        }

        return ScriptStepDefinition.ofMain(elementType)
    }
}
