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
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.auto.server.objects.job.value.JobDataValues
import kotlin.reflect.KType
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.ClassNames.asTopLevelImport


/**
 * Compiles contract-native Job expressions with ordinal record accessors and explicit keyed access.
 *
 * An expression sees every facet of the value through one scope, innermost first:
 * - the payload: `this` (the native payload when it has one, otherwise the record's fields), its members by bare
 *   name, and `payload` as an alias;
 * - the value's metadata ([DataContract.metadata]): each field a typed property by bare name (`year`,
 *   `parent.name`), and all of it as `meta`;
 * - the Job's parameters by bare name.
 * A bare name resolves to the innermost facet that has it; `this.x` and `meta.x` always reach the payload and the
 * metadata. A bare name the expression uses that an inner facet shadows is reported as a warning.
 */
class JobExpressionCompiler(
    private val cachedKotlinCompiler: CachedKotlinCompiler,
    private val kotlinSyntaxValidator: KotlinSyntaxValidator
) {
    companion object {
        const val metaName = "meta"
        const val payloadName = "payload"

        // An identifier not reached through a qualifier (`x.name`), bare or back-ticked
        private val bareName = Regex("""(?<![.\w`])(`[^`]+`|[A-Za-z_]\w*)""")
    }


    data class Compiled(
        val expression: JobCalculatedExpression<Any?>,
        val contract: DataContract,
        val streams: Boolean,
        val streamElementContract: DataContract?
    )

    data class Attempt(
        val compiled: Compiled?,
        val error: String?,
        val warnings: List<String> = emptyList()
    ) {
        val warning: String?
            get() = warnings.joinToString("; ").ifBlank { null }
    }


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
        input.metadata?.let { metadata -> unsupportedBoundary(metadata.structural) }
            ?.let { return Attempt(null, "Metadata: $it") }
        val warnings = shadowWarnings(expression, input, parameters)
        val attempt = compileScoped(name, expression, input, modelType, classLoader, parameters)
        return attempt.copy(warnings = warnings)
    }


    private fun compileScoped(
        name: String,
        expression: String,
        input: DataContract,
        modelType: TypeMetadata,
        classLoader: ClassLoader,
        parameters: BindingSchema
    ): Attempt {
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
        val streams = ExpressionReturnTypeInference.isStreamType(inferredType)
        // Design-time shapes come from the adapter registry (E7 item 6), so a record, bean, enum or Set typed
        // expression shows the columns the run will emit; a stream container itself is named, not described.
        val inferred = if (streams) {
            ExpressionReturnTypeInference.toTypeMetadata(inferredType).toDataContract()
        }
        else {
            val (described, error) = describe(inferredType)
            described ?: return Attempt(null, error)
        }
        unsupportedBoundary(inferred.structural)?.let { return Attempt(null, it) }
        val streamElement = if (streams) {
            val elementType = ExpressionReturnTypeInference.streamElementType(inferredType)
            if (elementType == null) {
                TypeMetadata.anyNullable.toDataContract()
            }
            else {
                val (described, error) = describe(elementType)
                described ?: return Attempt(null, error)
            }
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
        val payload = input.payload()
        val accessors = accessors(payload)
        val metadataClasses = metadataClasses(input.metadata ?: MetadataContract.empty)
        val imports = imports(
            modelType,
            parameters,
            accessors.map { it.type } + metadataClasses.flatMap { it.properties.mapNotNull { p -> p.type } })

        val accessorCode = accessors.joinToString("\n") { accessor ->
            "        val ${accessor.name} get(): ${accessor.type.toSimple()} {" +
                    " return field(${accessor.ordinal}) as ${accessor.type.toSimple()} }"
        }
        val keyedAccessCode = when (payload.structural) {
            is DataType.Dynamic,
            is DataType.Mapping,
            is DataType.Record ->
                "        fun key(name: String): Any? = JobExpressionValues.keyed(inputValue, name)"
            else -> ""
        }
        val parameterCode = parameters.definitions.withIndex().joinToString("\n") { indexed ->
            val accessorName = ExpressionUtils.escapeKotlinVariableName(indexed.value.name.value)
            val type = indexed.value.typeMetadata().toSimple()
            "    val $accessorName get(): $type { return parameterValues[${indexed.index}] as $type }"
        }
        val metadataCode = metadataClasses.joinToString("\n\n") { it.code() }

        // A payload with no native type of its own is its record fields: `this` is then the fields scope
        val metaScope = "${metadataClasses.first().name}(inputValue.metadata?.value)"
        val body =
            if (modelType.className != ClassNames.kotlinAny) {
                "with($metaScope) { with(Fields()) { with($payloadName) { run {\n$expression\n        } } } }"
            }
            else {
                "with($metaScope) { with(Fields()) { run {\n$expression\n        } } }"
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

$parameterCode

    inner class Fields {
$keyedAccessCode

$accessorCode
    }

$metadataCode

    override fun setParameters(values: List<Any?>) {
        parameterValues = values
    }

    override fun evaluate(model: $model, value: DataValue, projection: ColumnProjection?): Any? {
        this.inputValue = value
        this.projection = projection
        return $probe(model)
    }

    private val $probe = { $payloadName: $model ->
        $body
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
            is DataType.Reference,
            is DataType.Union -> emptyList()
        }


    //-----------------------------------------------------------------------------------------------------------------
    /** One generated class per metadata record: the root (which also answers to `meta`) and each nested record. */
    private class MetadataClass(
        val name: String,
        val root: Boolean,
        val properties: List<MetadataProperty>
    ) {
        fun code(): String {
            val lines = properties.joinToString("\n") { it.code() }
            val qualifier = if (root) "        val $metaName: $name get() = this\n" else ""
            // By name too, for metadata whose fields are known only once the value exists (a unit's attributes)
            val keyed = "        operator fun get(name: String): Any? = JobExpressionValues.metadataKeyed(node, name)\n"
            return "    class $name(private val node: DataValue?) {\n$qualifier$keyed$lines\n    }"
        }
    }


    /** A metadata field: a leaf read at the boundary with its [type], or a nested record's [recordClass]. */
    private class MetadataProperty(
        val name: String,
        val field: FieldId,
        val nullable: Boolean,
        val type: TypeMetadata?,
        val recordClass: String?
    ) {
        fun code(): String {
            val fieldName = field.name.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
            val fieldArgs = "node, \"$fieldName\", ${field.occurrence}"
            if (recordClass != null) {
                return if (nullable) {
                    "        val $name: $recordClass? get() = " +
                            "JobExpressionValues.metadataRecord($fieldArgs, true)?.let { $recordClass(it) }"
                }
                else {
                    "        val $name: $recordClass get() = " +
                            "$recordClass(JobExpressionValues.metadataRecord($fieldArgs, false))"
                }
            }
            val leafType = checkNotNull(type)
            val simple = leafType.toSimple()
            return "        val $name: $simple get() = " +
                    "JobExpressionValues.metadataField($fieldArgs, ${leafType.nullable}) as $simple"
        }
    }


    private fun metadataClasses(metadata: MetadataContract): List<MetadataClass> {
        val classes = mutableListOf<MetadataClass>()
        fun visit(contract: DataContract, root: Boolean): String {
            val index = classes.size
            val name = "Meta$index"
            classes += MetadataClass(name, root, emptyList())
            val record = contract.expanded().structural as? DataType.Record
            val properties = record?.fields.orEmpty().mapNotNull { field ->
                val propertyName = ExpressionUtils.escapeKotlinVariableName(
                    HeaderLabel(field.id.name, field.id.occurrence))
                if (root && propertyName == metaName) {
                    // The qualifier wins over a metadata field of the same name
                    return@mapNotNull null
                }
                val child = contract.child(DataPathSegment.Field(field.id))
                val nullable = field.optional || child.structural.nullable
                if (child.expanded().structural is DataType.Record) {
                    MetadataProperty(propertyName, field.id, nullable, null, visit(child, root = false))
                }
                else {
                    MetadataProperty(propertyName, field.id, nullable, child.typeMetadata(field.optional), null)
                }
            }
            classes[index] = MetadataClass(name, root, properties)
            return name
        }
        visit(metadata.contract, root = true)
        return classes
    }


    private fun metadataNames(metadata: MetadataContract?): Set<String> {
        val record = metadata?.structural
            ?: return emptySet()
        return record.fields.mapTo(mutableSetOf()) {
            ExpressionUtils.escapeKotlinVariableName(HeaderLabel(it.id.name, it.id.occurrence))
        }
    }


    /** Bare names [expression] uses that resolve to an inner facet while an outer facet also has them. */
    private fun shadowWarnings(expression: String, input: DataContract, parameters: BindingSchema): List<String> {
        val payloadNames = accessors(input.payload()).mapTo(mutableSetOf()) { it.name }
        val metadataNames = metadataNames(input.metadata)
        val used = bareName.findAll(expression).map { it.value }.toSet()

        val warnings = mutableListOf<String>()
        for (name in (payloadNames intersect metadataNames).filter { it in used }) {
            warnings += "'$name' is the payload's; the metadata's is $metaName.$name"
        }
        for (parameter in parameters.definitions) {
            val name = ExpressionUtils.escapeKotlinVariableName(parameter.name.value)
            if (name !in used) {
                continue
            }
            when (name) {
                in payloadNames -> warnings += "'$name' is the payload's, not the parameter"
                in metadataNames -> warnings += "'$name' is the metadata's, not the parameter"
            }
        }
        return warnings
    }


    /** Describes [type] through the run-time registry; a refused or conflicting type is the compile error text. */
    private fun describe(type: KType): Pair<DataContract?, String?> {
        return try {
            JobDataValues.describe(type) to null
        }
        catch (e: DataException) {
            null to "Job expression type $type: ${e.problem.message}"
        }
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
        is DataType.Reference,
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
        is DataType.Reference,
        is DataType.Union -> if (nullable) TypeMetadata.anyNullable else TypeMetadata.any
    }
}
