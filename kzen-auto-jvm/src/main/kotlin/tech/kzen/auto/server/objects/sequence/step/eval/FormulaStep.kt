@file:Suppress("ConstPropertyName")

package tech.kzen.auto.server.objects.sequence.step.eval

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.sequence.api.SequenceStepDefinition
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.ClassNames.simple


@Reflect
class FormulaStep(
    private val code: String,
    private val selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(FormulaStep::class.java)

        private val anyNullable = TypeMetadata(ClassNames.kotlinAny, listOf(), true)

        private const val inferredTypePrefix = "inferred type is "
        private const val inferredTypeSuffix = " but "

        private fun parseInferredType(errorMessage: String): String? {
            val startOfPrefix = errorMessage.indexOf(inferredTypePrefix)
            if (startOfPrefix == -1) {
                return null
            }

            val startOfInferred = startOfPrefix + inferredTypePrefix.length
            val endOfInferred = errorMessage.indexOf(inferredTypeSuffix, startIndex = startOfInferred)
            if (endOfInferred == -1) {
                return null
            }

            return errorMessage.substring(startOfInferred ..< endOfInferred)
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
            ClassName("kotlin.ranges.IntRange"))

        private fun parseTypeMetadata(inferredType: String): TypeMetadata? {
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

            val generics = genericComponents.map { parseTypeMetadata(it) }

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

            val simpleMatch = simpleClassNames.find { it.simple() == inferredTypeWithoutGenerics }
                ?: return null

            return TypeMetadata(
                simpleMatch, generics.mapNotNull { it }, nullable)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("FoldInitializerAndIfToElvis", "RedundantSuppression")
    override fun definition(): SequenceStepDefinition {
        val compiler = KzenAutoContext.global().cachedKotlinCompiler
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

        val anyNullableCode = generateCode("Any?")
        val anyNullableError = compiler.tryCompile(anyNullableCode, classLoader)
        if (anyNullableError != null) {
            return SequenceStepDefinition(
                TupleDefinition.ofMain(LogicType(anyNullable)),
                anyNullableError)
        }

        val anyCode = generateCode("Any")
        val anyError = compiler.tryCompile(anyCode, classLoader)

        val nullable = anyError != null
        val nullableSuffix = if (nullable) { "?" } else { "" }

        val stringCode = generateCode("String$nullableSuffix")
        val stringError = compiler.tryCompile(stringCode, classLoader)

        if (stringError == null) {
            return SequenceStepDefinition(
                TupleDefinition.ofMain(LogicType(
                    TypeMetadata(ClassNames.kotlinString, listOf(), nullable)
                )),
                null)
        }

        val inferredType = parseInferredType(stringError)
        if (inferredType == null) {
            return SequenceStepDefinition(
                TupleDefinition.ofMain(LogicType(
                    TypeMetadata(ClassNames.kotlinAny, listOf(), nullable)
                )),
                "Unable to infer type: $stringError")
        }

        val typeMetadata = parseTypeMetadata(inferredType)
        if (typeMetadata == null) {
            return SequenceStepDefinition(
                TupleDefinition.ofMain(LogicType(
                    TypeMetadata(ClassNames.kotlinAny, listOf(), nullable)
                )),
                "Unable to parse inferred type: $inferredType")
        }

//        println("^^ ERR: $stringError")

        return SequenceStepDefinition(
            TupleDefinition.ofMain(LogicType(typeMetadata)),
            null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun continueOrStart(
        stepContext: StepContext
    ): LogicResult {
        logger.info("{} - value = {}", selfLocation, code)

        val compiler = KzenAutoContext.global().cachedKotlinCompiler
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

        val code = generateCode("Any?")

        val error = compiler.tryCompile(code, classLoader)
        check(error == null) {
            "Unable to compile: $error - $code"
        }

        val clazz = compiler.tryLoad(code, classLoader)
        check(clazz != null) {
            "Unable to load: $code"
        }

        @Suppress("UNCHECKED_CAST")
        val classCast = clazz as Class<StepExpression>

        val instance = classCast.getDeclaredConstructor().newInstance()
        val value = instance.evaluate()
        println("Value: $value")

        traceValue(stepContext, value.toString())

        return LogicResultSuccess(
            TupleValue.ofMain(value))
    }


    private fun generateCode(returnType: String): KotlinCode {
        val sanitizedName = sanitizeName(selfLocation.objectPath.name.value)
        val mainClassName = "Eval_$sanitizedName"

        val imports = generateImports()

        val code = """
$imports

class $mainClassName: ${ StepExpression::class.java.simpleName } {
    override fun evaluate(): $returnType {
        return run {
$code
        }
    }
}
"""
        return KotlinCode(
            mainClassName,
            code)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun generateImports(): String {
        val classImports = setOf(
            StepExpression::class.java.name
        )

        return classImports.joinToString("\n") {
            "import $it"
        }
    }


    private fun sanitizeName(text: String): String {
        return text.replace(Regex("\\W+"), "_")
    }
}