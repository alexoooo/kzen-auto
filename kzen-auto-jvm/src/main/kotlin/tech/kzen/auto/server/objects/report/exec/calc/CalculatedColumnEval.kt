package tech.kzen.auto.server.objects.report.exec.calc

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.logic.ExpressionReturnTypeInference
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.auto.server.service.compile.KotlinSyntaxValidator
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.ClassNames.asTopLevelImport
import kotlin.reflect.KType


/**
 * Compiles a user Kotlin expression into a [CalculatedColumn] — the single expression engine shared by the
 * Report formula stage and the Job Workers (Filter / Formula / FormulaSource), with three scopes:
 *
 * - **The model (payload) as receiver**: the expression evaluates with [modelType] as its implicit receiver
 *   (Report's data model; a Job lane's typed payload), so the model's members are bare — and, by Kotlin's
 *   innermost-receiver rule, SHADOW same-named columns. The receiver is also bound to an explicit `payload`
 *   alias (the escape hatch, and the only handle on a nullable / untyped model, whose members are not bare).
 * - **Columns bare** ([columnNames]): each column becomes a [ColumnValue] accessor by its escaped name.
 * - **Parameters bare** ([parameters], a Job's declared inputs): each declaration becomes a bare typed
 *   accessor reading the run-constant values injected via [CalculatedColumn.setParameters] — the values are
 *   deliberately not baked into the generated source, so the compile cache keys on
 *   (expression, columns, model type, parameter types) only. A parameter whose (escaped) name collides with
 *   a column is rejected with a descriptive error before any compile, since both would generate the same
 *   class property.
 *
 * The user's expression is generated as the lambda-valued probe property the
 * [ExpressionReturnTypeInference] contract reflects, so ONE compile serves validation (the inferred value
 * type — [inferredReturnKType]), the Job payload-type walk, and execution.
 */
class CalculatedColumnEval(
    private val cachedKotlinCompiler: CachedKotlinCompiler,
    private val kotlinSyntaxValidator: KotlinSyntaxValidator
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun validate(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing,
        modelType: TypeMetadata,
        classLoader: ClassLoader,
        parameters: BindingSchema = BindingSchema.empty
    ): String? {
        if (calculatedColumnFormula.isEmpty()) {
            return null
        }

        collisionError(columnNames, parameters)?.let {
            return it
        }

        val code = generate(calculatedColumnName, calculatedColumnFormula, columnNames, modelType, parameters)
        val compileError = cachedKotlinCompiler.tryCompile(code, classLoader)
        return compileError?.let { cleanupErrorMessage(it.error) }
    }
    /**
     * The scope-independent half of [validate], for a caller whose column scope is NOT statically known (a Job
     * Worker on a CSV lane): reports malformed source, which no header could make compile, and stays silent on
     * everything a header would have to settle. [validate] is unusable there — with no column accessors
     * generated, every column reference would come back unresolved and a good expression would read as broken.
     */
    fun validateSyntax(calculatedColumnFormula: String): String? {
        return kotlinSyntaxValidator.validate(calculatedColumnFormula)
    }


    private fun cleanupErrorMessage(errorMessage: String): String {
        return errorMessage
            .replaceFirst("return (", "")
            .replaceFirst(")\n", "\n")
            .replaceFirst("        ^", "^")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: return with compilation / creation error?
    fun create(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing,
        modelType: TypeMetadata,
        classLoader: ClassLoader,
        parameters: BindingSchema = BindingSchema.empty
    ): CalculatedColumn<Any?> {
        if (calculatedColumnFormula.isEmpty()) {
            return ConstantCalculatedColumn.empty()
        }

        val collision = collisionError(columnNames, parameters)
        check(collision == null) {
            "$collision - $calculatedColumnName - $calculatedColumnFormula"
        }

        val code = generate(calculatedColumnName, calculatedColumnFormula, columnNames, modelType, parameters)

        val error = cachedKotlinCompiler.tryCompile(code, classLoader)
        check(error == null) {
            "Unable to compile: ${error?.error} - $calculatedColumnName - $calculatedColumnFormula - " +
                    columnNames.render()
        }

        val clazz = cachedKotlinCompiler.tryLoad(code, classLoader)
        check(clazz != null) {
            "Unable to load: $code"
        }

        @Suppress("UNCHECKED_CAST")
        val classCast = clazz as Class<CalculatedColumn<Any?>>

        return classCast.getDeclaredConstructor().newInstance()
    }
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The raw [KType] the compiler inferred for a [create]d column's expression (the probe contract —
     * [ExpressionReturnTypeInference]), so consumers (the payload-type walk, FormulaSourceWorker's static
     * stream-vs-single dispatch) never touch generated-class internals. Only meaningful for a generated
     * column — an empty-formula [ConstantCalculatedColumn] has no expression, hence null.
     */
    fun inferredReturnKType(column: CalculatedColumn<*>): KType? {
        if (column is ConstantCalculatedColumn<*>) {
            return null
        }
        return ExpressionReturnTypeInference.inferReturnKType(column::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A declared parameter and a column that escape to the same accessor name would generate duplicate class
    // properties — rejected up front with a message naming the culprit (rather than a cryptic Kotlin
    // duplicate-declaration error out of the generated source).
    private fun collisionError(columnNames: HeaderListing, parameters: BindingSchema): String? {
        if (parameters.definitions.isEmpty()) {
            return null
        }

        val columnAccessors = columnNames
            .values
            .map { ExpressionUtils.escapeKotlinVariableName(it) }
            .toSet()

        val collision = parameters.definitions.firstOrNull {
            ExpressionUtils.escapeKotlinVariableName(it.name.value) in columnAccessors
        } ?: return null

        return "Parameter '${collision.name.value}' collides with a column name - rename one of them"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun generate(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing,
        modelType: TypeMetadata,
        parameters: BindingSchema
    ): KotlinCode {
        val sanitizedName = sanitizeClassName(calculatedColumnName)
        val mainClassName = "Column_$sanitizedName"

        val imports = generateImports(modelType, parameters)

        val columnNameStringList = columnNames.values.joinToString { "\"${it.asString()}\""}

        val columnAccessors = generateColumnAccessors(columnNames)
        val parameterAccessors = generateParameterAccessors(parameters)

        val modelSimple = modelType.toSimple()

        // The probe holds the user's expression as a lambda so its value type is inferred (the
        // ExpressionReturnTypeInference contract; a lambda rather than a function declaration so a
        // `Nothing`-typed expression compiles under K2). The parameter doubles as the `payload` alias, and
        // `with` makes a (non-nullable) model's members bare — the innermost implicit receiver, shadowing
        // same-named column accessors.
        val probeName = ExpressionReturnTypeInference.probePropertyName

        val code = """
$imports

class $mainClassName: ${ CalculatedColumn::class.java.simpleName }<$modelSimple> {
    companion object {
        private val columnNames: HeaderListing = HeaderListing.ofCollection(listOf($columnNameStringList))
        private val recordHeaderIndex = ${ RecordHeaderIndex::class.java.simpleName }(columnNames)
    }

    private var indices = IntArray(0)
    private var record: ${ FlatFileRecord::class.java.simpleName } = ${ FlatFileRecord::class.java.simpleName }()
    private var parameterValues: List<Any?> = listOf()

    private fun columnValue(columnIndex: Int): ${ ColumnValue::class.java.simpleName } {
        val index = indices[columnIndex]
        val text = if (index == -1) { LegacyDataShapeBridge.missingCellValue } else { record.getString(index) }
        return ${ ColumnValue::class.java.simpleName }.ofText(text)
    }

$columnAccessors

$parameterAccessors

    override fun setParameters(values: List<Any?>) {
        parameterValues = values
    }

    override fun evaluate(
        model: $modelSimple,
        flatFileRecord: ${ FlatFileRecord::class.java.simpleName },
        headerListing: ${ HeaderListing::class.java.simpleName }
    ): ColumnValue {
        return ${ ColumnValue::class.java.simpleName }.ofScalar(
            evaluateRaw(model, flatFileRecord, headerListing))
    }


    override fun evaluateRaw(
        model: $modelSimple,
        flatFileRecord: ${ FlatFileRecord::class.java.simpleName },
        headerListing: ${ HeaderListing::class.java.simpleName }
    ): Any? {
        record = flatFileRecord
        indices = recordHeaderIndex.indices(headerListing)
        return $probeName(model)
    }


    private val $probeName = { payload: $modelSimple ->
        with(payload) {
            run {
$calculatedColumnFormula
            }
        }
    }
}
"""
        return KotlinCode(
            mainClassName,
            code)
    }


    private fun generateImports(modelType: TypeMetadata, parameters: BindingSchema): String {
        val operatorImports = ColumnValueConversions.operators.map {
            ColumnValueConversions::class.java.name + ".$it"
        }

        val parameterImports = parameters
            .definitions
            .flatMap { it.typeMetadata().classNames() }
            .map { it.asTopLevelImport() }

        val modelImports = modelType
            .classNames()
            .map { it.asTopLevelImport() }

        val classImports = setOf(
            CalculatedColumn::class.java.name,
            ColumnValue::class.java.name,
            LegacyDataShapeBridge::class.java.name,
            HeaderListing::class.java.name,
            RecordHeaderIndex::class.java.name,
            FlatFileRecord::class.java.name
        ) + modelImports + parameterImports

        val allImports: Set<String> = classImports + operatorImports

        return allImports.joinToString("\n") {
            "import $it"
        }
    }


    private fun generateColumnAccessors(headerListing: HeaderListing): String {
        val variableNames = headerListing
            .values
            .map { ExpressionUtils.escapeKotlinVariableName(it) }

        return variableNames
            .withIndex()
            .joinToString("\n") { columnName ->
                "val ${columnName.value} get(): ColumnValue {" +
                "    return columnValue(${columnName.index})" +
                "}"
            }
    }


    // Bare typed accessors for the declared parameters (Script's StepExpressionCompiler precedent): the
    // accessor name is the canonical escape of the declaration name, the type its declared TypeMetadata, and
    // the value the same-index element of the injected [setParameters] list.
    private fun generateParameterAccessors(parameters: BindingSchema): String {
        return parameters
            .definitions
            .withIndex()
            .joinToString("\n") {
                val accessorName = ExpressionUtils.escapeKotlinVariableName(it.value.name.value)
                val accessorType = it.value.typeMetadata().toSimple()
                "val $accessorName get(): $accessorType {" +
                "    return parameterValues[${it.index}] as $accessorType" +
                "}"
            }
    }

    private fun sanitizeClassName(text: String): String {
        return text.replace(Regex("\\W+"), "_")
    }


    private fun BindingDefinition.typeMetadata(): TypeMetadata =
        contract.nativeByPath[DataTypePath.root] ?: contract.structural.toTypeMetadata()


    private fun DataType.toTypeMetadata(): TypeMetadata =
        when (this) {
            is DataType.Dynamic -> if (nullable) TypeMetadata.anyNullable else TypeMetadata.any
            is DataType.Listing -> TypeMetadata(
                ClassNames.kotlinList,
                listOf(element.toTypeMetadata()),
                nullable)
            is DataType.Mapping -> TypeMetadata(
                ClassName("kotlin.collections.Map"),
                listOf(key.toTypeMetadata(), value.toTypeMetadata()),
                nullable)
            is DataType.Opaque,
            is DataType.Record,
            is DataType.Reference,
            is DataType.Union -> if (nullable) TypeMetadata.anyNullable else TypeMetadata.any
            is DataType.Scalar -> TypeMetadata(ClassName(when (val scalarKind = kind) {
                ScalarKind.Boolean -> "kotlin.Boolean"
                is ScalarKind.Integer -> when (scalarKind.bits) {
                    8 -> "kotlin.Byte"
                    16 -> "kotlin.Short"
                    32 -> "kotlin.Int"
                    else -> "kotlin.Long"
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
        }
}
