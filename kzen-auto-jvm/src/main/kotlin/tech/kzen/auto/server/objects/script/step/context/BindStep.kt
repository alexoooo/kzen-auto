package tech.kzen.auto.server.objects.script.step.context

import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.auto.server.objects.logic.ExpressionReturnTypeInference
import tech.kzen.auto.server.objects.logic.TypeAssignability
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.step.eval.StepExpressionSupport
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * Publishes a Kotlin expression's value into the run's ambient scope under the Context this step `binds` —
 * the flavour-neutral binder, where `BrowserOpenStep` is the same publication wrapped around a resource it
 * opens.
 *
 * `is: [ScriptStep, ContextBinder]` and deliberately NOT `ResourceOwner`: the binding carries no disposal,
 * so binding a String offers no close-policy control — not because an editor hides one, but because the
 * step declares no such attribute for anything to render. That is the whole point of the two mix-ins being
 * separate. Ending the name is a later [ReleaseStep]'s job, and with nothing attached it degenerates to
 * removing the name.
 */
@Reflect
class BindStep(
    private val value: String,
    private val qualifier: String,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val graphNotation = scriptDefinitionContext.graphNotation

        val descriptor = LogicContextConventions.stepBinds(graphNotation, selfLocation)
            ?: return ScriptStepDefinition(
                null,
                ContextStepMessages.unresolvedDeclaration(
                    graphNotation, selfLocation, LogicContextConventions.bindsAttributePath,
                    "No context to bind into — choose one"))

        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                selfLocation,
                scriptDefinitionContext.scriptTree,
                scriptDefinitionContext.scriptValidation))
            ?: return null

        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
        val generatedCode = StepExpressionSupport.generateInferenceCode(
            selfLocation, value, nonUnitPredecessorTypes)

        // A compile error means the user's expression itself is broken; it becomes this step's validation error.
        val compileError = cachedKotlinCompiler.tryCompile(generatedCode, classLoader)
        if (compileError != null) {
            return ScriptStepDefinition(null, compileError.error, compileError.userCodeOffset)
        }

        val clazz = cachedKotlinCompiler.tryLoad(generatedCode, classLoader)
            ?: return ScriptStepDefinition(null, "Unable to load: $generatedCode")

        // The STATIC half of the conformance the runtime bind re-checks by raw class: caught here, the
        // mismatch is reported against the expression that caused it rather than surfacing several steps
        // later inside whatever read the Context.
        val inferred = ExpressionReturnTypeInference.inferReturnType(clazz)

        val mismatch = conformanceMismatch(inferred, descriptor, classLoader)
        if (mismatch != null) {
            return ScriptStepDefinition(null, mismatch)
        }

        // The DECLARED type, not the inferred one. The declaration is the contract every downstream reader
        // was written against, so a narrower expression today must not silently narrow what they may assume
        // tomorrow — the assignability check above is what makes publishing the wider declared type sound.
        return ScriptStepDefinition.ofMain(descriptor.type)
    }


    /**
     * The static conformance verdict, or null when the expression may be bound.
     *
     * [ExpressionReturnTypeInference] approximates an unnameable classifier to `Any` and does not mark the
     * approximation — an expression genuinely typed `Any` reads identically. So `Any` here can mean *the
     * graph cannot name this type*, not only *this is the top type*, and asserting assignability from it would
     * reject values whose static classifier cannot be imported. The class comparison is therefore skipped for
     * that case and the runtime raw-class check in `ScriptRunContext.checkBindConformance` stays definitive.
     *
     * Nullability survives the approximation (it is read off the `KType`, not the classifier), so it is
     * checked either way — and it is the half a runtime check can only catch once a null actually arrives.
     */
    private fun conformanceMismatch(
        inferred: TypeMetadata,
        descriptor: ContextDescriptor,
        classLoader: ClassLoader
    ): String? {
        if (inferred.nullable && ! descriptor.type.nullable) {
            return "${descriptor.label()} holds ${descriptor.typeLabel()}, which is not nullable, " +
                    "but this expression can evaluate to null"
        }

        if (inferred.className == TypeMetadata.any.className) {
            return null
        }

        if (TypeAssignability.isAssignable(inferred, descriptor.type, cachedKotlinCompiler, classLoader)) {
            return null
        }

        return "${descriptor.label()} holds ${descriptor.typeLabel()}, " +
                "which this expression's ${inferred.toSimple()} cannot be bound to"
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun run(execution: StepExecution): Any? {
        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                selfLocation, execution.scriptTree, execution.scriptValidation))
            ?: error("Unresolved in-scope types for: $selfLocation")

        val bound = StepExpressionSupport.evaluate(
            selfLocation, "Any?", value, nonUnitPredecessorTypes,
            { execution.referencedValue(it) }, cachedKotlinCompiler,
            infer = true,
            instanceCache = { signature, factory -> execution.perRunSingleton(signature, factory) })

        // The disposal-free bind: nothing is torn down on this binding's account, so re-binding in a loop
        // supersedes with no closer running, and a later release only removes the name.
        execution.bindContext(bound, qualifier.ifEmpty { null })

        execution.traceDetail(bound)
        return bound
    }
}
