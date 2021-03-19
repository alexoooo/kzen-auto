package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.KotlinCode


class CalculatedColumnEval(
    private val cachedKotlinCompiler: CachedKotlinCompiler
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val packagePath = "eval"
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun validate(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing
    ): String? {
        if (calculatedColumnFormula.isEmpty()) {
            return null
        }

        val code = generate(calculatedColumnName, calculatedColumnFormula, columnNames)
        val errorMessage = cachedKotlinCompiler.tryCompile(code)
        return errorMessage?.let { cleanupErrorMessage(it) }
    }


    private fun cleanupErrorMessage(errorMessage: String): String {
        return errorMessage
            .replaceFirst("return (", "")
            .replaceFirst(")\n", "\n")
            .replaceFirst("        ^", "^")
    }


    // TODO: return with compilation / creation error?
    fun create(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing
    ): CalculatedColumn {
        if (calculatedColumnFormula.isEmpty()) {
            return ConstantCalculatedColumn.empty
        }

        val code = generate(calculatedColumnName, calculatedColumnFormula, columnNames)

        val error = cachedKotlinCompiler.tryCompile(code)
        check(error == null) {
            "Unable to compile: $error - $calculatedColumnName - $calculatedColumnFormula - $columnNames"
        }

        val clazz = cachedKotlinCompiler.tryLoad(code)
        check(clazz != null) {
            "Unable to load: $code"
        }

        @Suppress("UNCHECKED_CAST")
        val classCast = clazz as Class<CalculatedColumn>

        return classCast.getDeclaredConstructor().newInstance()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun generate(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: HeaderListing
    ): KotlinCode {
        val sanitizedName = sanitizeName(calculatedColumnName)
        val mainClassName = "Column_$sanitizedName"

        val columnNameStringList = columnNames.values.joinToString { "\"$it\""}

//        val userCode =
//            "return ($calculatedColumnFormula)"

        val columnAccessors = columnNames.values.withIndex().joinToString("\n") { columnName -> """
val `${columnName.value}` get(): ColumnValue {
    return columnValue(${columnName.index})
}
"""
        }

        val code = """
package $packagePath

import ${ CalculatedColumn::class.java.name }
import ${ ColumnValue::class.java.name }
${ColumnValueConversions.operators.joinToString("\n") {
    "import ${ ColumnValueConversions::class.java.name }.$it"
}}
import ${ RecordHeader::class.java.name }
import ${ RecordHeaderIndex::class.java.name }
import ${ RecordRowBuffer::class.java.name }
import ${ HeaderListing::class.java.name }


class $mainClassName: ${ CalculatedColumn::class.java.simpleName } {
    companion object {
        private val columnNames: HeaderListing = HeaderListing(listOf($columnNameStringList))
        private val recordHeaderIndex = ${ RecordHeaderIndex::class.java.simpleName }(columnNames)
    }

    private var indices = IntArray(0)
    private var record: ${ RecordRowBuffer::class.java.simpleName } = ${ RecordRowBuffer::class.java.simpleName }()

    private fun columnValue(columnIndex: Int): ${ ColumnValue::class.java.simpleName } {
        val index = indices[columnIndex]
        val text = if (index == -1) { "<missing>" } else { record.getString(index) }
        return ${ ColumnValue::class.java.simpleName }.ofText(text)
    }

$columnAccessors

    override fun evaluate(
        recordItemBuffer: ${ RecordRowBuffer::class.java.simpleName },
        recordHeader: ${ RecordHeader::class.java.simpleName }
    ): String {
        record = recordItemBuffer
        indices = recordHeaderIndex.indices(recordHeader)
        val value = evaluate()
        return ${ ColumnValue::class.java.simpleName }.toText(value)
    }


    private fun evaluate(): Any? {
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


    private fun sanitizeName(text: String): String {
        return text.replace(Regex("\\W+"), "_")
    }
}