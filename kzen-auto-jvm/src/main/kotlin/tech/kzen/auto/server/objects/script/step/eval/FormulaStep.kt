package tech.kzen.auto.server.objects.script.step.eval

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * A body step holding a Kotlin expression whose value type is inferred by the compiler. The generated
 * inference class ([StepExpressionSupport.generateInferenceCode]) is compiled once and serves both validation
 * — its inferred return type, read reflectively by [StepReturnTypeInference] — and execution — its `evaluate`.
 *
 * The step owns its own compilation, with no central compiler pre-baking it, which is what makes Script steps
 * third-party-extensible (cf. the Job FormulaSourceWorker).
 */
@Reflect
class FormulaStep(
    private val code: String,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun run(execution: StepExecution): Any? {
        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                selfLocation, execution.scriptTree, execution.scriptValidation))
            ?: error("Unresolved in-scope types for: $selfLocation")

        return StepExpressionSupport.evaluate(
            selfLocation, "Any?", code, nonUnitPredecessorTypes,
            { execution.referencedValue(it) }, cachedKotlinCompiler,
            infer = true,
            instanceCache = { signature, factory -> execution.perRunSingleton(signature, factory) })
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                selfLocation,
                scriptDefinitionContext.scriptTree,
                scriptDefinitionContext.scriptValidation))
            ?: return null

        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
        val generatedCode = StepExpressionSupport.generateInferenceCode(
            selfLocation, code, nonUnitPredecessorTypes)

        // A compile error means the user's expression itself is broken; it becomes this step's validation error.
        val compileError = cachedKotlinCompiler.tryCompile(generatedCode, classLoader)
        if (compileError != null) {
            return ScriptStepDefinition(null, compileError)
        }

        val clazz = cachedKotlinCompiler.tryLoad(generatedCode, classLoader)
            ?: return ScriptStepDefinition(
                TupleDefinition.ofMain(LogicType(TypeMetadata.anyNullable)),
                "Unable to load: $generatedCode")

        val typeMetadata = StepReturnTypeInference.inferReturnType(
            clazz, scriptDefinitionContext.objectRegistryScan)

        return ScriptStepDefinition(
            TupleDefinition.ofMain(LogicType(typeMetadata)),
            null)
    }
}
