package tech.kzen.auto.server.objects.script.step.control.foreach

import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.ScriptValueBinding
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


// Deprecated: superseded by ForEachItemBinding (rowless typed loop item). Kept as a non-executable binding so
// legacy notation still parses / validates; the new ForEachStep feeds the item to the `item`-branch binding,
// not to a body-row step, so this is never executed.
@Reflect
class ForEachItemStep(
    @Suppress("unused") private val selfLocation: ObjectLocation
):
    ScriptValueBinding()
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.any))
    }
}
