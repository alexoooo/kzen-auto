package tech.kzen.auto.server.objects.report.exec.stages

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.server.objects.report.exec.ReportPipelineStage
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumn
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.exec.calc.ColumnValue
import tech.kzen.auto.server.objects.report.exec.calc.ConstantCalculatedColumn
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import tech.kzen.lib.platform.ClassName


class ReportFormulaStage(
    private val modelType: ClassName,
    private val formulaSpec: FormulaSpec,
    private val classLoader: ClassLoader,
    private val calculatedColumnEval: CalculatedColumnEval
):
    ReportPipelineStage<ReportOutputEvent<*>>("formula")
{
    //-----------------------------------------------------------------------------------------------------------------
    private val formulaCount = formulaSpec.formulas.size
    private val formulas = Array<CalculatedColumn<Any>>(formulaCount) { ConstantCalculatedColumn.empty() }
    private val formulaValues = Array(formulaCount) { "" }

    private var previousHeader: HeaderListing? = null
    private var augmentedHeader: HeaderListing? = null


    //-----------------------------------------------------------------------------------------------------------------
    private fun getFormulasAndAugmentHeader(header: HeaderListing) {
        if (previousHeader == header) {
            // NB: optimization for reference equality
            previousHeader = header
            return
        }
        previousHeader = header

        var nextIndex = 0
        for (formula in formulaSpec.formulas) {
            val errorMessage = calculatedColumnEval.validate(
                formula.key, formula.value, header, modelType, classLoader)

            val calculatedColumn =
                if (errorMessage == null) {
                    calculatedColumnEval.create(
                        formula.key, formula.value, header, modelType, classLoader)
                }
                else {
                    ConstantCalculatedColumn.error()
                }

            formulas[nextIndex++] = calculatedColumn
        }

        augmentedHeader = header.append(HeaderListing.ofUnique(formulaSpec.formulas.keys.toList()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ReportOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (! event.isSkipOrSentinel()) {
            processFormulas(event)
        }
//        else {
//            println("saw sentinel: ${event.hasSentinel()}")
//        }

        event.model = null
    }


    private fun processFormulas(event: ReportOutputEvent<*>) {
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