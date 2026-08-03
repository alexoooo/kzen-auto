package tech.kzen.auto.server.exec.script.test

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy


/**
 * A test-only binder: reads back whatever its declared context currently holds (the replace-existing path
 * every real binder has), records it, then binds [value] under that same context.
 *
 * The read is deliberately ARGUMENT-FREE. A binder declares no `uses` — the spine's uniform gate would
 * otherwise fail it before it could ever bind — so the only descriptor it has to resolve against is its
 * own `binds`. That is exactly what this step pins.
 *
 * [closePolicy] is a plain String parsed here rather than the `ResourceOwner` mix-in the production steps
 * use: the mix-in's `SelectValuesEditor` binding drags a JS-only `AttributeEditorManager` reference into the
 * JVM-only test graph, where it fails to resolve (see [OpenResourceTestStep]). The `binds:` declaration
 * itself is a weak reference (the archetype carries its own `by: Nominal` meta entry) and has no such
 * hazard, so it IS declared on the archetype.
 */
class ProvideContextTestStep(
    private val value: String,
    private val qualifier: String,
    closePolicy: String
):
    ScriptStep
{
    private val closePolicy = ResourceClosePolicy.parse(closePolicy)


    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val prior = execution.contextValueOrNull(qualifier = qualifier.ifEmpty { null })
        ContextProbeLog.record("provide[$value] saw ${prior ?: "nothing"}")

        execution.bindContext(value, closePolicy, qualifier.ifEmpty { null }) {
            ContextProbeLog.record("disposed[$value]")
        }
        return value
    }
}
