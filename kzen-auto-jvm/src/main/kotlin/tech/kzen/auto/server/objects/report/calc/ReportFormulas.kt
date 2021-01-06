package tech.kzen.auto.server.objects.report.calc

import tech.kzen.auto.server.objects.report.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.model.ReportFormulaSignature


class ReportFormulas(
    private val reportFormulaSignature: ReportFormulaSignature,
    private val calculatedColumnEval: CalculatedColumnEval
) {
    private var previousHeader: RecordHeader? = null
    private var previousFormulas: Map<String, CalculatedColumn> = mapOf()


    fun formulas(header: RecordHeader): Map<String, CalculatedColumn> {
        if (previousHeader == header) {
            // NB: optimization for reference equality
            previousHeader = header
            return previousFormulas
        }
        previousHeader = header

        val formulas = mutableMapOf<String, CalculatedColumn>()
        for (formula in reportFormulaSignature.formula.formulas) {
            val errorMessage = calculatedColumnEval.validate(
                formula.key, formula.value, header.headerNames)

            val calculatedColumn =
                if (errorMessage == null) {
                    calculatedColumnEval.create(
                        formula.key, formula.value, header.headerNames)
                }
                else {
                    ConstantCalculatedColumn.error
                }

            formulas[formula.key] = calculatedColumn
        }
        previousFormulas = formulas
        return formulas
    }


    fun evaluate(item: RecordLineBuffer, header: RecordHeader) {
        val formulas = formulas(header)

        for (formula in reportFormulaSignature.formula.formulas) {
            val calculatedColumn = formulas[formula.key]!!
            val calculatedValue = calculatedColumn.evaluate(item, header)
            item.add(calculatedValue)
        }
    }
}