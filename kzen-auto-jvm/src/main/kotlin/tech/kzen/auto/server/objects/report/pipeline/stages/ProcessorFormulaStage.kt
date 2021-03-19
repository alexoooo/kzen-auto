package tech.kzen.auto.server.objects.report.pipeline.stages

import com.lmax.disruptor.EventHandler
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.server.objects.report.pipeline.calc.CalculatedColumn
import tech.kzen.auto.server.objects.report.pipeline.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.pipeline.calc.ConstantCalculatedColumn
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader


class ProcessorFormulaStage(
    private val formulaSpec: FormulaSpec,
    private val calculatedColumnEval: CalculatedColumnEval
):
    EventHandler<ProcessorOutputEvent<*>>
{
    //-----------------------------------------------------------------------------------------------------------------
    private val formulaCount = formulaSpec.formulas.size
    private val formulas = Array<CalculatedColumn>(formulaCount) { ConstantCalculatedColumn.empty }
    private val formulaValues = Array(formulaCount) { "" }

//    @Volatile
    private var previousHeader: RecordHeader? = null
    private var augmentedHeader: RecordHeader? = null


    //-----------------------------------------------------------------------------------------------------------------
    private fun getFormulasAndAugmentHeader(header: RecordHeader) {
        if (previousHeader == header) {
            // NB: optimization for reference equality
            previousHeader = header
            return
        }
        previousHeader = header

        var nextIndex = 0
        for (formula in formulaSpec.formulas) {
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
            header.headerNames.values + formulaSpec.formulas.keys)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.skip) {
            return
        }

        val row = event.row
        val headerBuffer = event.header

        val header = headerBuffer.value
        getFormulasAndAugmentHeader(header)
        headerBuffer.value = augmentedHeader!!

        val formulaValuesLocal = formulaValues
        val formulasLocal = formulas

        for (i in 0 until formulaCount) {
            formulaValuesLocal[i] = formulasLocal[i].evaluate(row, header)
        }

        row.addAll(formulaValuesLocal)
    }
}