package tech.kzen.auto.server.objects.job.expression

import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.auto.server.objects.job.value.ColumnProjection
import tech.kzen.auto.server.objects.logic.ExpressionReturnTypeInference
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.auto.server.service.compile.KotlinSyntaxValidator
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.ClassNames.asTopLevelImport


/** Compiles contract-native Job expressions with ordinal record accessors and explicit keyed access. */
class JobExpressionCompiler(
    private val cachedKotlinCompiler: CachedKotlinCompiler,
    private val kotlinSyntaxValidator: KotlinSyntaxValidator
) {
    data class Compiled(
        val expression: JobCalculatedExpression<Any?>,
        val contract: DataContract,
        val streams: Boolean,
        val streamElementContract: DataContract?
    )

    data class Attempt(
        val compiled: Compiled?,
        val error: String?
    )


    fun validateSyntax(expression: String): String? =
        kotlinSyntaxValidator.validate(expression)


    fun compile(
        name: String,
        expression: String,
        input: DataContract,
        modelType: TypeMetadata,
        classLoader: ClassLoader,
        parameters: BindingSchema = BindingSchema.empty
    ): Attempt {
        unsupportedBoundary(input.structural)?.let { return Attempt(null, it) }
        collisionError(input, parameters)?.let { return Attempt(null, it) }
        val code = generate(name, expression, input, modelType, parameters)
        val error = cachedKotlinCompiler.tryCompile(code, classLoader)
        if (error != null) {
            return Attempt(null, cleanup(error.error))
        }
        val clazz = cachedKotlinCompiler.tryLoad(code, classLoader)
            ?: return Attempt(null, "Unable to load compiled Job expression")
        @Suppress("UNCHECKED_CAST")
        val generated = clazz.getDeclaredConstructor().newInstance() as JobCalculatedExpression<Any?>
        val inferredType = ExpressionReturnTypeInference.inferReturnKType(clazz)
        val inferred = ExpressionReturnTypeInference.toTypeMetadata(inferredType).toDataContract()
        unsupportedBoundary(inferred.structural)?.let { return Attempt(null, it) }
        val streams = ExpressionReturnTypeInference.isStreamType(inferredType)
        val streamElement = if (streams) {
            ExpressionReturnTypeInference.streamElementType(inferredType)
                ?.let(ExpressionReturnTypeInference::toTypeMetadata)
                ?.toDataContract()
                ?: TypeMetadata.anyNullable.toDataContract()
        }
        else {
            null
        }
        streamElement?.structural?.let { unsupportedBoundary(it) }
            ?.let { return Attempt(null, it) }
        return Attempt(Compiled(generated, inferred, streams, streamElement), null)
    }


    internal fun generate(
        name: String,
        expression: String,
        input: DataContract,
        modelType: TypeMetadata,
        parameters: BindingSchema = BindingSchema.empty
    ): KotlinCode {
        val className = "JobExpression_${name.replace(Regex("\\W+"), "_")}"
        val accessors = accessors(input)
        val imports = imports(modelType, parameters, accessors.map { it.type })
        val accessorCode = accessors.joinToString("\n") { accessor ->
            "val ${accessor.name} get(): ${accessor.type.toSimple()} {" +
                    " return field(${accessor.ordinal}) as ${accessor.type.toSimple()} }"
        }
        val keyedAccessCode = when (input.structural) {
            is DataType.Dynamic,
            is DataType.Mapping,
            is DataType.Record ->
                "fun key(name: String): Any? = JobExpressionValues.keyed(inputValue, name)"
            else -> ""
        }
        val parameterCode = parameters.definitions.withIndex().joinToString("\n") { indexed ->
            val accessorName = ExpressionUtils.escapeKotlinVariableName(indexed.value.name.value)
            val type = indexed.value.typeMetadata().toSimple()
            "val $accessorName get(): $type { return parameterValues[${indexed.index}] as $type }"
        }
        val model = modelType.toSimple()
        val probe = ExpressionReturnTypeInference.probePropertyName
        val source = """
$imports

class $className: JobCalculatedExpression<$model> {
    private lateinit var inputValue: DataValue
    private var projection: ColumnProjection? = null
    private var parameterValues: List<Any?> = listOf()

    private fun field(ordinal: Int): Any? =
        JobExpressionValues.projected(checkNotNull(projection), ordinal)

$keyedAccessCode

$accessorCode

$parameterCode

    override fun setParameters(values: List<Any?>) {
        parameterValues = values
    }

    override fun evaluate(model: $model, value: DataValue, projection: ColumnProjection?): Any? {
        this.inputValue = value
        this.projection = projection
        return $probe(model)
    }

    private val $probe = { payload: $model ->
        with(payload) {
            run {
$expression
            }
        }
    }
}
"""
        return KotlinCode(className, source)
    }


    private fun accessors(contract: DataContract): List<Accessor> =
        when (val structural = contract.structural) {
            is DataType.Record -> structural.fields.mapIndexed { index, field ->
                val child = contract.child(DataPathSegment.Field(field.id))
                Accessor(
                    ExpressionUtils.escapeKotlinVariableName(
                        HeaderLabel(field.id.name, field.id.occurrence)),
                    index,
                    child.typeMetadata(field.optional))
            }
            is DataType.Scalar -> listOf(Accessor(
                "value",
                0,
                contract.typeMetadata(optional = false)))
            is DataType.Dynamic,
            is DataType.Mapping,
            is DataType.Listing,
            is DataType.Opaque,
            is DataType.Union -> emptyList()
        }


    private fun collisionError(contract: DataContract, parameters: BindingSchema): String? {
        val names = accessors(contract).mapTo(mutableSetOf()) { it.name }
        val collision = parameters.definitions.firstOrNull {
            ExpressionUtils.escapeKotlinVariableName(it.name.value) in names
        } ?: return null
        return "Parameter '${collision.name.value}' collides with an input field - rename one of them"
    }


    private fun unsupportedBoundary(type: DataType): String? = when (type) {
        is DataType.Scalar -> when (val kind = type.kind) {
            is ScalarKind.Integer -> when {
                kind.bits == null ->
                    "Job expression boundary does not support unbounded integers"
                !kind.signed && kind.bits == 64 ->
                    "Job expression boundary does not support unsigned 64-bit integers"
                else -> null
            }
            else -> null
        }
        is DataType.Record -> type.fields.firstNotNullOfOrNull { unsupportedBoundary(it.type) }
        is DataType.Mapping -> unsupportedBoundary(type.key) ?: unsupportedBoundary(type.value)
        is DataType.Listing -> unsupportedBoundary(type.element)
        is DataType.Union -> type.variants.firstNotNullOfOrNull { unsupportedBoundary(it.type) }
        is DataType.Dynamic,
        is DataType.Opaque -> null
    }


    private fun imports(
        modelType: TypeMetadata,
        parameters: BindingSchema,
        accessorTypes: List<TypeMetadata>
    ): String {
        val names = modelType.classNames() +
                parameters.definitions.flatMap { it.typeMetadata().classNames() } +
                accessorTypes.flatMap { it.classNames() }
        return (setOf(
            JobCalculatedExpression::class.java.name,
            JobExpressionValues::class.java.name,
            ColumnProjection::class.java.name,
            DataValue::class.java.name,
            "java.math.BigDecimal") + names.map { it.asTopLevelImport() })
            .joinToString("\n") { "import $it" }
    }


    private fun cleanup(message: String): String = message
        .replaceFirst("return (", "")
        .replaceFirst(")\n", "\n")
        .replaceFirst("        ^", "^")


    private data class Accessor(
        val name: String,
        val ordinal: Int,
        val type: TypeMetadata
    )


    private fun BindingDefinition.typeMetadata(): TypeMetadata =
        contract.typeMetadata(optional = false)


    private fun DataContract.typeMetadata(optional: Boolean): TypeMetadata {
        val metadata = nativeByPath[DataTypePath.root] ?: structural.typeMetadata()
        return if (!optional || metadata.nullable) metadata
        else TypeMetadata(metadata.className, metadata.generics, nullable = true)
    }


    private fun DataType.typeMetadata(): TypeMetadata = when (this) {
        is DataType.Dynamic -> if (nullable) TypeMetadata.anyNullable else TypeMetadata.any
        is DataType.Listing -> TypeMetadata(ClassNames.kotlinList, listOf(element.typeMetadata()), nullable)
        is DataType.Mapping -> TypeMetadata(
            ClassName("kotlin.collections.Map"),
            listOf(key.typeMetadata(), value.typeMetadata()),
            nullable)
        is DataType.Scalar -> TypeMetadata(ClassName(when (val scalarKind = kind) {
            ScalarKind.Boolean -> "kotlin.Boolean"
            is ScalarKind.Integer -> when {
                scalarKind.bits == 8 && scalarKind.signed -> "kotlin.Byte"
                scalarKind.bits == 16 && scalarKind.signed -> "kotlin.Short"
                scalarKind.bits == 32 && scalarKind.signed -> "kotlin.Int"
                scalarKind.bits == 64 && scalarKind.signed -> "kotlin.Long"
                scalarKind.bits == 8 -> "kotlin.UByte"
                scalarKind.bits == 16 -> "kotlin.UShort"
                scalarKind.bits == 32 -> "kotlin.UInt"
                else -> error("Unsupported Job expression integer boundary: $scalarKind")
            }
            ScalarKind.Decimal -> "java.math.BigDecimal"
            is ScalarKind.Floating -> if (scalarKind.bits == 32) "kotlin.Float" else "kotlin.Double"
            ScalarKind.Text -> "kotlin.String"
            ScalarKind.Binary -> "kotlin.ByteArray"
            ScalarKind.Date -> "java.time.LocalDate"
            ScalarKind.Time -> "java.time.LocalTime"
            ScalarKind.Instant -> "java.time.Instant"
            ScalarKind.Duration -> "java.time.Duration"
            ScalarKind.Uuid -> "java.util.UUID"
        }), emptyList(), nullable)
        is DataType.Opaque,
        is DataType.Record,
        is DataType.Union -> if (nullable) TypeMetadata.anyNullable else TypeMetadata.any
    }
}
