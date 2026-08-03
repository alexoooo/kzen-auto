package tech.kzen.auto.server.objects.script.step.context

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.reflect.Reflect


/**
 * Ends a Context: the binding goes, and the disposal its binder attached runs — the flavour-neutral closer,
 * where `BrowserCloseStep` is the same operation named for one Context.
 *
 * Two invariants shape it. It names WHAT ends and never tears the handle down itself: only the binder knows
 * how its own handle dies, so the engine runs that disposal, at most once, instead of every closer
 * duplicating it and the handle being torn down twice. And a plain (disposal-free) binding has nothing
 * attached, so releasing one degenerates safely to removing the name.
 *
 * There is deliberately NO remove-without-dispose mode here. Dropping a name while its resource stays open
 * has the opposite failure mode — a silent leak instead of an early teardown — so if it is ever wanted it
 * must be a separately named `unbind` step, never a flag or a policy hiding behind this one, where a user
 * who picks the wrong value leaks a browser.
 *
 * Declares `releases:` rather than `uses:`, so the spine never gates it: a closer's job is to make the
 * absence true, which makes "nothing was bound" success.
 */
@Reflect
class ReleaseStep(
    private val qualifier: String
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val qualifierOrNull = qualifier.ifEmpty { null }

        // Read BEFORE releasing, because afterwards there is by construction nothing to report. Distinguishing
        // the two outcomes is the whole value of the trace here: "nothing was bound" is success, and a user
        // staring at a resource that outlived the run needs to see which of the two happened.
        val released = execution.contextValueOrNull(qualifier = qualifierOrNull) != null

        // Offloaded because the disposal runs on the calling thread and is whatever the binder attached — a
        // browser quit, a process kill — so it may genuinely block the engine dispatcher.
        execution.blocking { execution.releaseContext(qualifier = qualifierOrNull) }

        val label = execution.declaredContexts().singleOrNull()?.label() ?: "context"
        execution.traceDetail(
            if (released) { "Released $label" }
            else { "Nothing bound for $label" })
        return null
    }
}
