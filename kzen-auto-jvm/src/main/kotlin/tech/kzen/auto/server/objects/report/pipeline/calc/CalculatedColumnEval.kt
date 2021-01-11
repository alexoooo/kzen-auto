package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeaderIndex
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
        columnNames: List<String>
    ): String? {
        if (calculatedColumnFormula.isEmpty()) {
            return null
        }

        val code = generate(calculatedColumnName, calculatedColumnFormula, columnNames)
        return cachedKotlinCompiler.tryCompile(code)
    }


    // TODO: return with compilation / creation error?
    fun create(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: List<String>
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
        columnNames: List<String>
    ): KotlinCode {
        val sanitizedName = sanitizeName(calculatedColumnName)
        val mainClassName = "Column_$sanitizedName"

        val columnNameStringList = columnNames.joinToString { "\"$it\""}

        val userCode =
            if (calculatedColumnFormula.contains("return ")) {
                calculatedColumnFormula
            }
            else {
                "return ($calculatedColumnFormula)"
            }

        val columnAccessors = columnNames.withIndex().joinToString("\n") { columnName -> """
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
import ${ RecordItemBuffer::class.java.name }


class $mainClassName: ${ CalculatedColumn::class.java.simpleName } {
    companion object {
        private val columnNames: List<String> = listOf($columnNameStringList)
        private val recordHeaderIndex = ${ RecordHeaderIndex::class.java.simpleName }(columnNames)
    }

    private var indices = IntArray(0)
    private var record: ${ RecordItemBuffer::class.java.simpleName } = ${ RecordItemBuffer::class.java.simpleName }()

    private fun columnValue(columnIndex: Int): ${ ColumnValue::class.java.simpleName } {
        val index = indices[columnIndex]
        val text = if (index == -1) { "<missing>" } else { record.getString(index) }
        return ${ ColumnValue::class.java.simpleName }.ofText(text)
    }

$columnAccessors

    override fun evaluate(
        recordItemBuffer: ${ RecordItemBuffer::class.java.simpleName },
        recordHeader: ${ RecordHeader::class.java.simpleName }
    ): String {
        record = recordItemBuffer
        indices = recordHeaderIndex.indices(recordHeader)
        val value = evaluate()
        return ${ ColumnValue::class.java.simpleName }.toText(value)
    }


    private fun evaluate(): Any? {
$userCode
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