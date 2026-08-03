package tech.kzen.auto.server.objects.script.step.context

import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Reads a Context out of the run's ambient scope and into the value graph, so the ordinary value steps —
 * a Formula referencing it by name, a Display showing it — can consume it without knowing Contexts exist.
 * `BrowserGetStep`'s `execution.context<RemoteWebDriver>()` is the hand-written case of this.
 *
 * Its published type is the Context's declared contract, which is what makes the read useful: an expression
 * naming this step gets a typed accessor rather than `Any`.
 */
@Reflect
class UseContextStep(
    private val qualifier: String,
    private val selfLocation: ObjectLocation
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        val graphNotation = scriptDefinitionContext.graphNotation
        val used = LogicContextConventions.stepUses(graphNotation, selfLocation)

        return when (used.size) {
            0 -> ScriptStepDefinition(
                null,
                ContextStepMessages.unresolvedDeclaration(
                    graphNotation, selfLocation, LogicContextConventions.usesAttributePath,
                    "No context to read — choose one"))

            1 -> ScriptStepDefinition.of(TupleDefinition.ofMain(LogicType(used.single().type)))

            // The base `uses` is a list and stays one for the steps that genuinely read several; this step
            // publishes ONE value, so it has no answer for a second Context. Reachable only by hand-editing
            // the notation into the list form the editor does not write.
            else -> ScriptStepDefinition(
                null,
                "Reads one context, but ${used.size} are named: ${used.joinToString { it.label() }}")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun run(execution: StepExecution): Any? {
        // No absence check: this step declares `uses`, so the spine's uniform gate already failed it before
        // `run` was entered when nothing is bound — the body only ever sees a live value. Argument-free,
        // resolving against the single declaration; the qualifier addresses one member of that family, and
        // is the only thing the family-wide gate cannot see.
        return execution.contextValue(qualifier = qualifier.ifEmpty { null })
    }
}
