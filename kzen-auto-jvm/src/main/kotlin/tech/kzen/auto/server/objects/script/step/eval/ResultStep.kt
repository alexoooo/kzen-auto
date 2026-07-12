package tech.kzen.auto.server.objects.script.step.eval

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * Sets the Script's return value: a body step holding a Kotlin expression that must evaluate to the
 * Script's declared `main` result type (the result signature at the top of the stage). The last Result
 * step executed wins (Visual-Basic style) — its value is captured via [StepExecution.setResult] and
 * returned by the Script once it completes. With no declared result signature the Script is void, so a
 * Result step is a validation error; a type mismatch surfaces as the step's compile error like [FormulaStep].
 */
@Reflect
class ResultStep(
    private val code: String,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val noResultDeclared = "No result type declared in the Script signature"
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Evaluate the expression as the Script's declared `main` result type and capture it as the result (last
    // Result step wins). Like [FormulaStep] the step compiles its own expression with its injected compiler.
    override suspend fun run(execution: StepExecution): Any? {
        val declaredType = declaredMainType(execution.resultSignature)
            ?: error("Result step with no declared result type: $selfLocation")

        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                selfLocation, execution.scriptTree, execution.scriptValidation))
            ?: error("Unresolved in-scope types for: $selfLocation")

        val value = StepExpressionSupport.evaluate(
            selfLocation, declaredType.toSimple(), code, nonUnitPredecessorTypes,
            { execution.referencedValue(it) }, cachedKotlinCompiler,
            instanceCache = { signature, factory -> execution.perRunSingleton(signature, factory) })

        execution.setResult(TupleValue.ofMain(value))
        return value
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val declaredType = declaredMainType(scriptDefinitionContext.resultSignature)
            ?: return ScriptStepDefinition(null, noResultDeclared)

        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                selfLocation,
                scriptDefinitionContext.scriptTree,
                scriptDefinitionContext.scriptValidation))
            ?: return null

        // Compile the expression with the declared result type as the forced return type: a value that does
        // not conform yields a compile error, which becomes this step's validationError (the type mismatch).
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
        val generatedCode = StepExpressionSupport.generateCode(
            selfLocation, declaredType.toSimple(), code, nonUnitPredecessorTypes)
        val compileError = cachedKotlinCompiler.tryCompile(generatedCode, classLoader)

        return ScriptStepDefinition(
            TupleDefinition.ofMain(LogicType(declaredType)),
            compileError)
    }


    private fun declaredMainType(resultSignature: TupleDefinition): TypeMetadata? {
        return resultSignature.find(TupleComponentName.main)?.metadata
    }
}
