@file:Suppress("ConstPropertyName")

package tech.kzen.auto.server.objects.sequence.step.eval

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.sequence.model.SequenceTree
import tech.kzen.auto.common.objects.document.sequence.model.SequenceValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.sequence.api.SequenceStepDefinition
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.SequenceDefinitionContext
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultFailed
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
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

        private const val inferredTypePrefix = "inferred type is "
        private const val inferredTypeSuffix = " but "

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
            ClassName("kotlin.ranges.IntRange"))


        private fun findClassName(inferredTypeWithoutGenerics: String): ClassName? {
            val simpleClassName = simpleClassNames.find { it.simple() == inferredTypeWithoutGenerics }
            if (simpleClassName != null) {
                return simpleClassName
            }

            return null
        }


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

            val simpleMatch = findClassName(inferredTypeWithoutGenerics)
                ?: return null

            return TypeMetadata(
                simpleMatch, generics.mapNotNull { it }, nullable)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("FoldInitializerAndIfToElvis", "RedundantSuppression")
    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition? {
        val compiler = KzenAutoContext.global().cachedKotlinCompiler
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

        val predecessorTypesNullable = processorTypes(
            sequenceDefinitionContext.sequenceTree, sequenceDefinitionContext.sequenceValidation)

        val predecessorTypes = predecessorTypesNullable
            .filter { it.value != null }
            .mapValues { it.value!! }

        if (predecessorTypes.size != predecessorTypesNullable.size) {
            return null
        }

        val nonUnitPredecessorTypes = predecessorTypes
            .filter { it.value.className != ClassNames.kotlinUnit }

        val anyNullableCode = generateCode(
            "Any?", nonUnitPredecessorTypes)
        val anyNullableError = compiler.tryCompile(anyNullableCode, classLoader)
        if (anyNullableError != null) {
            return SequenceStepDefinition(
//                TupleDefinition.ofMain(LogicType(TypeMetadata.anyNullable)),
                null,
                anyNullableError)
        }

        val anyCode = generateCode(
            "Any", nonUnitPredecessorTypes)
        val anyError = compiler.tryCompile(anyCode, classLoader)

        val nullable = anyError != null
        val nullableSuffix = if (nullable) { "?" } else { "" }

        val stringCode = generateCode(
            "String$nullableSuffix", nonUnitPredecessorTypes)
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
        sequenceExecutionContext: SequenceExecutionContext
    ): LogicResult {
        logger.info("{} - value = {}", selfLocation, code)

        val compiler = KzenAutoContext.global().cachedKotlinCompiler
//        val graphDefinitionAttempt = KzenAutoContext.global().graphStore.graphDefinition()
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

        val predecessorTypesNullable = processorTypes(
            sequenceExecutionContext.sequenceTree, sequenceExecutionContext.sequenceValidation)

        val predecessorTypes = predecessorTypesNullable
            .filter { it.value != null }
            .mapValues { it.value!! }

        if (predecessorTypes.size != predecessorTypesNullable.size) {
            val missingPredecessor = predecessorTypesNullable.filter { it.value == null }.keys
            return LogicResultFailed("Can't determine type: $missingPredecessor")
        }

        val nonUnitPredecessorTypes = predecessorTypes
            .filter { it.value.className != ClassNames.kotlinUnit }

        val generatedCode = generateCode(
            "Any?", nonUnitPredecessorTypes)

        val error = compiler.tryCompile(generatedCode, classLoader)
        check(error == null) {
            "Unable to compile: $error - $generatedCode"
        }

        val clazz = compiler.tryLoad(generatedCode, classLoader)
        check(clazz != null) {
            "Unable to load: $generatedCode"
        }

        @Suppress("UNCHECKED_CAST")
        val classCast = clazz as Class<StepExpression>

        val instance = classCast.getDeclaredConstructor().newInstance()

        val predecessorValues = nonUnitPredecessorTypes.map {
            val objectLocation = selfLocation.documentPath.toObjectLocation(it.key)
            val step = sequenceExecutionContext.activeSequenceModel.steps[objectLocation]
            step?.value?.mainComponentValue()
        }

        val value = instance.evaluate(predecessorValues)

        traceValue(sequenceExecutionContext, value.toString())

        return LogicResultSuccess(
            TupleValue.ofMain(value))
    }


    private fun processorTypes(
        sequenceTree: SequenceTree,
        sequenceValidation: SequenceValidation
    ):
        Map<ObjectPath, TypeMetadata?>
    {
        val builder = mutableMapOf<ObjectPath, TypeMetadata?>()
        val predecessors = sequenceTree.predecessors(selfLocation.objectPath)

        for (predecessor in predecessors) {
            val typeMetadata = sequenceValidation.stepValidations[predecessor]?.typeMetadata
            builder[predecessor] = typeMetadata
        }

        return builder
    }


    private fun generateCode(
        returnType: String,
        predecessorTypes: Map<ObjectPath, TypeMetadata>
    ): KotlinCode {
        val sanitizedName = sanitizeName(selfLocation.objectPath.name.value)
        val mainClassName = "Eval_$sanitizedName"

        val imports = generateImports(predecessorTypes.values)

        val predecessorAccessors = predecessorTypes
            .entries
            .withIndex()
            .map {
                val entry = it.value
                "val `${entry.key.name.value}` get(): ${entry.value.toSimple()} {" +
                "    return predecessorValues[${it.index}] as ${entry.value.toSimple()}" +
                "}"
            }

        val generatedCode = """
$imports

class $mainClassName: ${ StepExpression::class.java.simpleName } {
    private var predecessorValues: List<Any?> = listOf()

    ${predecessorAccessors.joinToString("\n")}

    override fun evaluate(predecessorValues: List<Any?>): $returnType {
        this.predecessorValues = predecessorValues
        return run {
$code
        }
    }
}
"""
        return KotlinCode(
            mainClassName,
            generatedCode)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun generateImports(importTypeMetadata: Collection<TypeMetadata>): String {
        val classNames = importTypeMetadata.flatMap { it.classNames() }.toSet()

        val basicClassNames = setOf(
            StepExpression::class.java.name)

        val classImports = basicClassNames + classNames

        return classImports.joinToString("\n") {
            "import $it"
        }
    }


    private fun sanitizeName(text: String): String {
        return text.replace(Regex("\\W+"), "_")
    }
}