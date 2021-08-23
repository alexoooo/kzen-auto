package tech.kzen.auto.server.objects.pipeline.exec.stages

import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.server.objects.pipeline.exec.PipelineProcessorStage
import tech.kzen.auto.server.objects.report.pipeline.calc.CalculatedColumn
import tech.kzen.auto.server.objects.report.pipeline.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.pipeline.calc.ColumnValue
import tech.kzen.auto.server.objects.report.pipeline.calc.ConstantCalculatedColumn
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader
import tech.kzen.lib.platform.ClassName


class ProcessorFormulaStage(
    private val modelType: ClassName,
    private val formulaSpec: FormulaSpec,
    private val classLoader: ClassLoader,
    private val calculatedColumnEval: CalculatedColumnEval
):
    PipelineProcessorStage<ProcessorOutputEvent<*>>("formula")
{
    //-----------------------------------------------------------------------------------------------------------------
    private val formulaCount = formulaSpec.formulas.size
    private val formulas = Array<CalculatedColumn<Any>>(formulaCount) { ConstantCalculatedColumn.empty() }
    private val formulaValues = Array(formulaCount) { "" }

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
                formula.key, formula.value, header.headerNames, modelType, classLoader)

            val calculatedColumn =
                if (errorMessage == null) {
                    calculatedColumnEval.create(
                        formula.key, formula.value, header.headerNames, modelType, classLoader)
                }
                else {
                    ConstantCalculatedColumn.error()
                }

            formulas[nextIndex++] = calculatedColumn
        }

        augmentedHeader = RecordHeader.of(
            header.headerNames.values + formulaSpec.formulas.keys)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (! event.skip) {
            processFormulas(event)
        }

        event.model = null
    }


    private fun processFormulas(event: ProcessorOutputEvent<*>) {
        val row = event.row
        val headerBuffer = event.header
        val model = event.model ?: Unit

        val header = headerBuffer.value
        getFormulasAndAugmentHeader(header)
        headerBuffer.value = augmentedHeader!!

        val formulaValuesLocal = formulaValues
        val formulasLocal = formulas

        for (i in 0 until formulaCount) {
            val value = formulasLocal[i].evaluate(model, row, header)
            formulaValuesLocal[i] = ColumnValue.toText(value)
        }

        row.addAll(formulaValuesLocal)
    }
}