package tech.kzen.auto.server.objects.script.step.eval

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
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
 * step executed wins (Visual-Basic style) — its value is captured in [ScriptExecutionContext.setMainResult]
 * and returned by the Script once it completes. With no declared result signature the Script is void, so a
 * Result step is a validation error; a type mismatch surfaces as the step's compile error like [FormulaStep].
 */
@Reflect
class ResultStep(
    private val code: String,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ResultStep::class.java)

        private const val noResultDeclared = "No result type declared in the Script signature"
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


    override fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult {
        logger.info("{} - value = {}", selfLocation, code)

        val declaredType = declaredMainType(scriptExecutionContext.resultSignature)
            ?: return LogicResultFailed(noResultDeclared)

        val inScopeTypes = StepExpressionSupport.inScopeTypes(
            selfLocation,
            scriptExecutionContext.scriptTree,
            scriptExecutionContext.scriptValidation)

        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(inScopeTypes)
            ?: return LogicResultFailed(
                "Can't determine type: ${inScopeTypes.filter { it.value == null }.keys}")

        val value = StepExpressionSupport.evaluate(
            selfLocation, declaredType.toSimple(), code, nonUnitPredecessorTypes,
            scriptExecutionContext, cachedKotlinCompiler)

        traceValue(scriptExecutionContext, value.toString())

        // Last invoked Result step wins — capture as the Script's result. The step also returns its own
        // value (so it renders like any other step), but the Script-level result comes from this holder.
        scriptExecutionContext.setMainResult(value)

        return LogicResultSuccess(
            TupleValue.ofMain(value))
    }


    private fun declaredMainType(resultSignature: TupleDefinition): TypeMetadata? {
        return resultSignature.find(TupleComponentName.main)?.metadata
    }
}
