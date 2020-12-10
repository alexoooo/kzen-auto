package tech.kzen.auto.server.objects.report.calc

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
    fun check(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: List<String>
    ): String? {
        val code = generate(calculatedColumnName, calculatedColumnFormula, columnNames)
        return cachedKotlinCompiler.tryCompile(code)
    }


    fun eval(
        calculatedColumnName: String,
        calculatedColumnFormula: String,
        columnNames: List<String>
    ): CalculatedColumn {
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

        val columnAccessors = columnNames.withIndex().joinToString("\n") { columnName ->
            """
    val `${columnName.value}` get(): ColumnValue {
        val index = indices[${columnName.index}]
        val text = if (index == -1) { "<missing>"} else { record.getString(index) }
        return ColumnValue(text)
    }
            """.trimIndent()

        }

        val code = """
package $packagePath

import tech.kzen.auto.server.objects.report.calc.CalculatedColumn
import tech.kzen.auto.server.objects.report.calc.ColumnValue
import tech.kzen.auto.server.objects.report.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.input.model.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer


class $mainClassName: CalculatedColumn {
    companion object {
        private val columnNames: List<String> = listOf($columnNameStringList)
        private val recordHeaderIndex = RecordHeaderIndex(columnNames)
    }

    private var indices = IntArray(0)
    private var record: RecordLineBuffer = RecordLineBuffer()

$columnAccessors

    override fun evaluate(
        recordLineBuffer: RecordLineBuffer,
        recordHeader: RecordHeader
    ): String {
        record = recordLineBuffer
        indices = recordHeaderIndex.indices(recordHeader)

        return evaluate()?.toString() ?: "null"
    }


    private fun evaluate(): Any? {
$userCode
    }
}
        """.trimIndent()

        return KotlinCode(
            packagePath,
            mainClassName,
            code)
    }


    private fun sanitizeName(text: String): String {
        return text.replace(Regex("\\W+"), "_")
    }
}