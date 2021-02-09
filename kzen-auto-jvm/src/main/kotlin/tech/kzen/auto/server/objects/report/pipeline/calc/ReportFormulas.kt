package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.model.ReportFormulaSignature
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeaderBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer


class ReportFormulas(
    private val reportFormulaSignature: ReportFormulaSignature,
    private val calculatedColumnEval: CalculatedColumnEval
) {
    private var previousHeader: RecordHeader? = null
    private var augmentedHeader: RecordHeader? = null

    private val formulaCount = reportFormulaSignature.formula.formulas.size
    private val formulas = Array<CalculatedColumn>(formulaCount) { ConstantCalculatedColumn.empty }
    private val formulaValues = Array(formulaCount) { "" }


    private fun getFormulasAndAugmentHeader(header: RecordHeader) {
        if (previousHeader == header) {
            // NB: optimization for reference equality
            previousHeader = header
            return
        }
        previousHeader = header

        var nextIndex = 0
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

            formulas[nextIndex++] = calculatedColumn
        }
        augmentedHeader = RecordHeader.of(
            header.headerNames + reportFormulaSignature.formula.formulas.keys)
    }


    fun evaluate(item: RecordItemBuffer, headerBuffer: RecordHeaderBuffer) {
        val header = headerBuffer.value
        getFormulasAndAugmentHeader(header)
        headerBuffer.value = augmentedHeader!!

        for (i in 0 until formulaCount) {
            formulaValues[i] = formulas[i].evaluate(item, header)
        }
        item.addAllAndPopulateCaches(formulaValues)
    }
}