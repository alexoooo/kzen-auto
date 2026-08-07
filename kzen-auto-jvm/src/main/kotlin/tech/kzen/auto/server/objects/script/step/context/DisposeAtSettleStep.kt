package tech.kzen.auto.server.objects.script.step.context

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.step.eval.StepExpressionSupport
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * Registers frame cleanup with no value and no name: run [code] when the document that owns this step
 * settles. "Delete this file", "kill this helper" — work with nothing anyone would want to read, so no
 * Context is invented purely to obtain a `finally`.
 *
 * [disposal] is a [SettleDisposalPolicy], NOT the `ResourceClosePolicy` a managed binding takes. Two values
 * rather than three, and the missing one is not an omission: `manual` is a promotion — it hands a
 * registration one frame up so a later step can still find it and close it — and an anonymous registration
 * has no name for anything to find it by, so offering it would be a choice nothing could act on.
 */
@Reflect
class DisposeAtSettleStep(
    private val code: String,
    /**
     * A display string, shown as this step's trace detail. It introduces no namespace and supports no
     * lookup — nothing ever resolves it — which is exactly why an anonymous registration cannot be `manual`.
     */
    private val label: String,
    private val disposal: SettleDisposalPolicy,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                selfLocation,
                scriptDefinitionContext.scriptTree,
                scriptDefinitionContext.scriptValidation))
            ?: return null

        val generatedCode = StepExpressionSupport.generateInferenceCode(
            selfLocation, code, nonUnitPredecessorTypes)

        // The INFERENCE form, so the source validated here is byte-for-byte the source `run` executes (one
        // content signature, one compile). Its inferred type is never read: a cleanup expression is run for
        // its effect, and forcing a `Unit` return would reject the `delete()` / `destroy()` calls that are
        // the whole point. A compile error is the user's expression being broken, so it becomes this step's
        // validation error.
        val compileError = cachedKotlinCompiler.tryCompile(
            generatedCode, ClassLoaderUtils.dynamicParentClassLoader())

        if (compileError != null) {
            return ScriptStepDefinition(null, compileError.error, compileError.userCodeOffset)
        }

        return ScriptStepDefinition.empty
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun run(execution: StepExecution): Any? {
        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                selfLocation, execution.scriptTree, execution.scriptValidation))
            ?: error("Unresolved in-scope types for: $selfLocation")

        // Compile, load and bind the expression to a SNAPSHOT of its in-scope values NOW. The closer fires at
        // the owning frame's settle, when the run that could resolve a reference is over, so it must already
        // hold everything it needs — the same "dispose what you CAPTURED, never re-resolve by name" contract
        // every closer handed to `bindContext` is under.
        //
        // No `instanceCache`: a compiled expression instance carries its argument list as mutable state, so
        // reusing one across a loop's iterations would leave several pending closers sharing it and racing at
        // settle. A fresh instance per registration keeps each snapshot its own; the compile itself is
        // content-cached, so the loop still costs one `newInstance` and no recompilation.
        val deferred = StepExpressionSupport.prepare(
            selfLocation, "Any?", code, nonUnitPredecessorTypes,
            { execution.referencedValue(it) }, cachedKotlinCompiler,
            infer = true)

        execution.disposeAtSettle(disposal) { deferred() }

        execution.traceDetail(label.ifEmpty { "Cleanup registered" })
        return null
    }
}
