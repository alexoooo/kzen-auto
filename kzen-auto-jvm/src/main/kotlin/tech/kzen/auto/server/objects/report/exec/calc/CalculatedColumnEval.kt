package tech.kzen.auto.server.objects.report.exec.calc

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames.asTopLevelImport
import tech.kzen.lib.platform.ClassNames.topLevel


/**
 * Compiles a user Kotlin expression over a flat record's columns (referenced by name) into a [CalculatedColumn]
 * — the single expression engine shared by the Report formula stage and the Job Workers (Filter / Formula /
 * FormulaSource). Beyond the column accessors, the generated class optionally exposes a typed PARAMETER scope
 * ([parameters], a Job's declared inputs): each declaration becomes a bare typed accessor reading the
 * run-constant values injected via [CalculatedColumn.setParameters] — the values are deliberately not baked
 * into the generated source, so the compile cache keys on (expression, columns, parameter types) only. A
 * parameter whose (escaped) name collides with a column is rejected with a descriptive error before any
 * compile, since both would generate the same class property.
 */
class CalculatedColumnEval(
    private val cachedKotlinCompiler: CachedKotlinCompiler
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun validate(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing,
        modelType: ClassName,
        classLoader: ClassLoader,
        parameters: TupleDefinition = TupleDefinition.empty
    ): String? {
        if (calculatedColumnFormula.isEmpty()) {
            return null
        }

        collisionError(columnNames, parameters)?.let {
            return it
        }

        val code = generate(calculatedColumnName, calculatedColumnFormula, columnNames, modelType, parameters)
        val errorMessage = cachedKotlinCompiler.tryCompile(code, classLoader)
        return errorMessage?.let { cleanupErrorMessage(it) }
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
        modelType: ClassName,
        classLoader: ClassLoader,
        parameters: TupleDefinition = TupleDefinition.empty
    ): CalculatedColumn<Any> {
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
            "Unable to compile: $error - $calculatedColumnName - $calculatedColumnFormula - ${columnNames.render()}"
        }

        val clazz = cachedKotlinCompiler.tryLoad(code, classLoader)
        check(clazz != null) {
            "Unable to load: $code"
        }

        @Suppress("UNCHECKED_CAST")
        val classCast = clazz as Class<CalculatedColumn<Any>>

        return classCast.getDeclaredConstructor().newInstance()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A declared parameter and a column that escape to the same accessor name would generate duplicate class
    // properties — rejected up front with a message naming the culprit (rather than a cryptic Kotlin
    // duplicate-declaration error out of the generated source).
    private fun collisionError(columnNames: HeaderListing, parameters: TupleDefinition): String? {
        if (parameters.components.isEmpty()) {
            return null
        }

        val columnAccessors = columnNames
            .values
            .map { ExpressionUtils.escapeKotlinVariableName(it) }
            .toSet()

        val collision = parameters.components.firstOrNull {
            ExpressionUtils.escapeKotlinVariableName(it.name.value) in columnAccessors
        } ?: return null

        return "Parameter '${collision.name.value}' collides with a column name - rename one of them"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun generate(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing,
        modelType: ClassName,
        parameters: TupleDefinition
    ): KotlinCode {
        val sanitizedName = sanitizeClassName(calculatedColumnName)
        val mainClassName = "Column_$sanitizedName"

        val imports = generateImports(modelType, parameters)

        val columnNameStringList = columnNames.values.joinToString { "\"${it.asString()}\""}

        val columnAccessors = generateColumnAccessors(columnNames)
        val parameterAccessors = generateParameterAccessors(parameters)

        val code = """
$imports

class $mainClassName: ${ CalculatedColumn::class.java.simpleName }<${modelType.topLevel()}> {
    companion object {
        private val columnNames: HeaderListing = HeaderListing.ofCollection(listOf($columnNameStringList))
        private val recordHeaderIndex = ${ RecordHeaderIndex::class.java.simpleName }(columnNames)
    }

    private var indices = IntArray(0)
    private var record: ${ FlatFileRecord::class.java.simpleName } = ${ FlatFileRecord::class.java.simpleName }()
    private var parameterValues: List<Any?> = listOf()

    private fun columnValue(columnIndex: Int): ${ ColumnValue::class.java.simpleName } {
        val index = indices[columnIndex]
        val text = if (index == -1) { "<missing>" } else { record.getString(index) }
        return ${ ColumnValue::class.java.simpleName }.ofText(text)
    }

$columnAccessors

$parameterAccessors

    override fun setParameters(values: List<Any?>) {
        parameterValues = values
    }

    override fun evaluate(
        model: ${ modelType.topLevel() },
        flatFileRecord: ${ FlatFileRecord::class.java.simpleName },
        headerListing: ${ HeaderListing::class.java.simpleName }
    ): ColumnValue {
        return ${ ColumnValue::class.java.simpleName }.ofScalar(
            evaluateRaw(model, flatFileRecord, headerListing))
    }


    override fun evaluateRaw(
        model: ${ modelType.topLevel() },
        flatFileRecord: ${ FlatFileRecord::class.java.simpleName },
        headerListing: ${ HeaderListing::class.java.simpleName }
    ): Any? {
        record = flatFileRecord
        indices = recordHeaderIndex.indices(headerListing)
        return model.evaluate()
    }


    private fun ${ modelType.topLevel() }.evaluate(): Any? {
        return run {
$calculatedColumnFormula
        }
    }
}
"""
        return KotlinCode(
            mainClassName,
            code)
    }


    private fun generateImports(modelType: ClassName, parameters: TupleDefinition): String {
        val operatorImports = ColumnValueConversions.operators.map {
            ColumnValueConversions::class.java.name + ".$it"
        }

        val parameterImports = parameters
            .components
            .flatMap { it.type.metadata.classNames() }
            .map { it.asTopLevelImport() }

        val classImports = setOf(
            CalculatedColumn::class.java.name,
            ColumnValue::class.java.name,
            HeaderListing::class.java.name,
            RecordHeaderIndex::class.java.name,
            FlatFileRecord::class.java.name,
            modelType.asTopLevelImport()
        ) + parameterImports

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
    private fun generateParameterAccessors(parameters: TupleDefinition): String {
        return parameters
            .components
            .withIndex()
            .joinToString("\n") {
                val accessorName = ExpressionUtils.escapeKotlinVariableName(it.value.name.value)
                val accessorType = it.value.type.metadata.toSimple()
                "val $accessorName get(): $accessorType {" +
                "    return parameterValues[${it.index}] as $accessorType" +
                "}"
            }
    }

    private fun sanitizeClassName(text: String): String {
        return text.replace(Regex("\\W+"), "_")
    }
}
