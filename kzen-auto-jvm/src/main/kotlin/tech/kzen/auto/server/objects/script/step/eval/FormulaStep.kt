@file:Suppress("ConstPropertyName")

package tech.kzen.auto.server.objects.script.step.eval

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryScan
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
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.ClassNames.simple


@Reflect
class FormulaStep(
    private val code: String,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(FormulaStep::class.java)

        private const val inferredTypePrefix = "actual '"
        private const val inferredTypeSuffix = "'"

        private const val literalTypePrefix = "The "
        private const val literalTypeSuffix = " literal "
        private const val integerLiteralPrefix = "IntegerLiteralType["


        private fun parseInferredType(errorMessage: String): String? {
            val startOfPrefix = errorMessage.indexOf(inferredTypePrefix)
            if (startOfPrefix == -1) {
                return parseLiteralType(errorMessage)
            }

            val startOfInferred = startOfPrefix + inferredTypePrefix.length
            val endOfInferred = errorMessage.indexOf(inferredTypeSuffix, startIndex = startOfInferred)
            if (endOfInferred == -1) {
                return null
            }

            val parsedType = errorMessage.substring(startOfInferred ..< endOfInferred)
            if (parsedType.startsWith(integerLiteralPrefix)) {
                return ClassNames.kotlinInt.simple()
            }

            return parsedType
        }


        private fun parseLiteralType(errorMessage: String): String? {
            val startOfPrefix = errorMessage.indexOf(literalTypePrefix)
            if (startOfPrefix == -1) {
                return null
            }

            val startOfLiteral = startOfPrefix + literalTypePrefix.length
            val endOfLiteral = errorMessage.indexOf(literalTypeSuffix, startIndex = startOfLiteral)
            if (endOfLiteral == -1) {
                return null
            }

            @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
            val literalName = errorMessage.substring(startOfLiteral ..< endOfLiteral)

            return when (literalName) {
                "integer" -> ClassNames.kotlinInt.simple()
                "floating-point" -> ClassNames.kotlinDouble.simple()
                "boolean" -> ClassNames.kotlinBoolean.simple()
                else -> TODO("Unexpected literal: $literalName")
            }
        }


        private val simpleClassNames = listOf(
            ClassNames.kotlinUnit,
            ClassNames.kotlinAny,
            ClassNames.kotlinString,
            ClassNames.kotlinBoolean,
            ClassNames.kotlinInt,
            ClassNames.kotlinLong,
            ClassNames.kotlinDouble,
            ClassNames.kotlinList,
            ClassNames.kotlinSet,
//            ClassName("kotlin.ranges.IntRange")
        )


        private fun findClassName(
            inferredTypeWithoutGenerics: String,
            objectRegistryScan: ObjectRegistryScan
        ): ClassName? {
            val simpleClassName = simpleClassNames
                .find { it.simple() == inferredTypeWithoutGenerics }

            if (simpleClassName != null) {
                return simpleClassName
            }

            return objectRegistryScan
                .classNames
                .find { it.simple() == inferredTypeWithoutGenerics }
        }


        private fun parseTypeMetadata(
            inferredType: String,
            objectRegistryScan: ObjectRegistryScan
        ): TypeMetadata? {
            val intersectionComponents = inferredType.split(" & ")
            val mostSpecificComponent = intersectionComponents.last().removeSuffix("}")

            val nullable = mostSpecificComponent.endsWith("?")
            val startOfGenerics = mostSpecificComponent.indexOf("<")

            val genericComponents: List<String> =
                if (startOfGenerics == -1) {
                    listOf()
                }
                else {
                    mostSpecificComponent
                        .substring(startOfGenerics + 1, mostSpecificComponent.length - 1)
                        .split(",")
                }

            val generics = genericComponents.map {
                parseTypeMetadata(it, objectRegistryScan)
            }

            val genericsParseErrors = generics
                .withIndex()
                .filter { it.value == null }
                .map { it.index }
            if (genericsParseErrors.isNotEmpty()) {
                return null
            }

            val inferredTypeWithoutGenerics =
                if (startOfGenerics == -1) {
                    mostSpecificComponent
                }
                else {
                    mostSpecificComponent.substring(0, startOfGenerics)
                }

            val simpleMatch = findClassName(inferredTypeWithoutGenerics, objectRegistryScan)
                ?: return null

            return TypeMetadata(
                simpleMatch, generics.mapNotNull { it }, nullable)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("FoldInitializerAndIfToElvis", "RedundantSuppression")
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val compiler = cachedKotlinCompiler
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                selfLocation,
                scriptDefinitionContext.scriptTree,
                scriptDefinitionContext.scriptValidation))
            ?: return null

        val anyNullableCode = StepExpressionSupport.generateCode(
            selfLocation, "Any?", code, nonUnitPredecessorTypes)
        val anyNullableError = compiler.tryCompile(anyNullableCode, classLoader)
        if (anyNullableError != null) {
            return ScriptStepDefinition(
//                TupleDefinition.ofMain(LogicType(TypeMetadata.anyNullable)),
                null,
                anyNullableError)
        }

        val anyCode = StepExpressionSupport.generateCode(
            selfLocation, "Any", code, nonUnitPredecessorTypes)
        val anyError = compiler.tryCompile(anyCode, classLoader)

        val nullable = anyError != null
        val nullableSuffix = if (nullable) { "?" } else { "" }

        val stringCode = StepExpressionSupport.generateCode(
            selfLocation, "String$nullableSuffix", code, nonUnitPredecessorTypes)
        val stringError = compiler.tryCompile(stringCode, classLoader)

        if (stringError == null) {
            return ScriptStepDefinition(
                TupleDefinition.ofMain(LogicType(
                    TypeMetadata(ClassNames.kotlinString, listOf(), nullable)
                )),
                null)
        }

        val inferredType = parseInferredType(stringError)
        if (inferredType == null) {
            return ScriptStepDefinition(
                TupleDefinition.ofMain(LogicType(
                    TypeMetadata(ClassNames.kotlinAny, listOf(), nullable)
                )),
                "Unable to infer type: $stringError")
        }

        val typeMetadata = parseTypeMetadata(inferredType, scriptDefinitionContext.objectRegistryScan)
        if (typeMetadata == null) {
            return ScriptStepDefinition(
                TupleDefinition.ofMain(LogicType(
                    TypeMetadata(ClassNames.kotlinAny, listOf(), nullable)
                )),
                "Unable to parse inferred type: $inferredType")
        }

//        println("^^ ERR: $stringError")

        return ScriptStepDefinition(
            TupleDefinition.ofMain(LogicType(typeMetadata)),
            null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun continueOrStart(
        scriptExecutionContext: ScriptExecutionContext
    ): LogicResult {
        logger.info("{} - value = {}", selfLocation, code)

        val inScopeTypes = StepExpressionSupport.inScopeTypes(
            selfLocation,
            scriptExecutionContext.scriptTree,
            scriptExecutionContext.scriptValidation)

        val nonUnitPredecessorTypes = StepExpressionSupport.resolveNonUnit(inScopeTypes)
            ?: return LogicResultFailed(
                "Can't determine type: ${inScopeTypes.filter { it.value == null }.keys}")

        val value = StepExpressionSupport.evaluate(
            selfLocation, "Any?", code, nonUnitPredecessorTypes,
            scriptExecutionContext, cachedKotlinCompiler)

        traceValue(scriptExecutionContext, value.toString())

        return LogicResultSuccess(
            TupleValue.ofMain(value))
    }
}