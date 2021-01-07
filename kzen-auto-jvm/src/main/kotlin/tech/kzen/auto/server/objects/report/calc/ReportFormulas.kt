package tech.kzen.auto.server.objects.report.calc

import tech.kzen.auto.server.objects.report.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.input.model.RecordHeaderBuffer
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.model.ReportFormulaSignature


class ReportFormulas(
    private val reportFormulaSignature: ReportFormulaSignature,
    private val calculatedColumnEval: CalculatedColumnEval
) {
    private var previousHeader: RecordHeader? = null
    private var augmentedHeader: RecordHeader? = null
    private var formulas: Map<String, CalculatedColumn> = mapOf()


    fun getFormulasAndAugmentHeader(header: RecordHeader) {
        if (previousHeader == header) {
            // NB: optimization for reference equality
            previousHeader = header
            return
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
        this.formulas = formulas
        augmentedHeader = RecordHeader.of(
            header.headerNames + reportFormulaSignature.formula.formulas.keys)
    }


    fun evaluate(item: RecordLineBuffer, headerBuffer: RecordHeaderBuffer) {
        val header = headerBuffer.value
        getFormulasAndAugmentHeader(header)
        headerBuffer.value = augmentedHeader!!

        for (formula in reportFormulaSignature.formula.formulas) {
            val calculatedColumn = formulas[formula.key]!!
            val calculatedValue = calculatedColumn.evaluate(item, header)
            item.add(calculatedValue)
        }
    }
}