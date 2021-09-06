package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.pipeline.exec.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.pipeline.exec.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames.asTopLevelImport
import tech.kzen.lib.platform.ClassNames.topLevel


class CalculatedColumnEval(
    private val cachedKotlinCompiler: CachedKotlinCompiler
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val packagePath = ""
//        private const val packagePath = "eval"
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun validate(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing,
        modelType: ClassName,
        classLoader: ClassLoader
    ): String? {
        if (calculatedColumnFormula.isEmpty()) {
            return null
        }

        val code = generate(calculatedColumnName, calculatedColumnFormula, columnNames, modelType)
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
        classLoader: ClassLoader
    ): CalculatedColumn<Any> {
        if (calculatedColumnFormula.isEmpty()) {
            return ConstantCalculatedColumn.empty()
        }

        val code = generate(calculatedColumnName, calculatedColumnFormula, columnNames, modelType)

        val error = cachedKotlinCompiler.tryCompile(code, classLoader)
        check(error == null) {
            "Unable to compile: $error - $calculatedColumnName - $calculatedColumnFormula - $columnNames"
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
    private fun generate(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing,
        modelType: ClassName
    ): KotlinCode {
        val sanitizedName = sanitizeName(calculatedColumnName)
        val mainClassName = "Column_$sanitizedName"

        val imports = generateImports(modelType)

        val columnNameStringList = columnNames.values.joinToString { "\"$it\""}

        val columnAccessors = generateColumnAccessors(columnNames)

        val code = """
${
    if (packagePath.isEmpty()) {
        ""
    }
    else {
        "package $packagePath"
    }
}

$imports

class $mainClassName: ${ CalculatedColumn::class.java.simpleName }<${modelType.topLevel()}> {
    companion object {
        private val columnNames: HeaderListing = HeaderListing(listOf($columnNameStringList))
        private val recordHeaderIndex = ${ RecordHeaderIndex::class.java.simpleName }(columnNames)
    }

    private var indices = IntArray(0)
    private var record: ${ FlatFileRecord::class.java.simpleName } = ${ FlatFileRecord::class.java.simpleName }()

    private fun columnValue(columnIndex: Int): ${ ColumnValue::class.java.simpleName } {
        val index = indices[columnIndex]
        val text = if (index == -1) { "<missing>" } else { record.getString(index) }
        return ${ ColumnValue::class.java.simpleName }.ofText(text)
    }

$columnAccessors

    override fun evaluate(
        model: ${ modelType.topLevel() },
        flatFileRecord: ${ FlatFileRecord::class.java.simpleName },
        recordHeader: ${ RecordHeader::class.java.simpleName }
    ): ColumnValue {
        record = flatFileRecord
        indices = recordHeaderIndex.indices(recordHeader)
        val value = model.evaluate()
        return ${ ColumnValue::class.java.simpleName }.ofScalar(value)
    }


    private fun ${ modelType.topLevel() }.evaluate(): Any? {
        return run {
$calculatedColumnFormula
        }
    }
}
"""
        return KotlinCode(
            packagePath,
            mainClassName,
            code)
    }


    private fun generateImports(modelType: ClassName): String {
        val operatorImports = ColumnValueConversions.operators.map {
            ColumnValueConversions::class.java.name + ".$it"
        }

        val classImports = setOf(
            CalculatedColumn::class.java.name,
            ColumnValue::class.java.name,
            RecordHeader::class.java.name,
            RecordHeaderIndex::class.java.name,
            FlatFileRecord::class.java.name,
            HeaderListing::class.java.name,
            modelType.asTopLevelImport()
        )

        val allImports: Set<String> = classImports + operatorImports

        return allImports.joinToString("\n") {
            "import $it"
        }
    }


    private fun generateColumnAccessors(columnNames: HeaderListing): String {
        return columnNames
            .values
            .withIndex()
            .joinToString("\n") { columnName ->
                "val `${columnName.value}` get(): ColumnValue {" +
                "    return columnValue(${columnName.index})" +
                "}"
            }
    }


    private fun sanitizeName(text: String): String {
        return text.replace(Regex("\\W+"), "_")
    }
}