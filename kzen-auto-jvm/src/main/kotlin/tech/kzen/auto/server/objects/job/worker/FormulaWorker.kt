package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumn
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.exec.calc.ColumnValue
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassNames


/**
 * M3 Report-parity slice — the calculated-column stage as a Job Worker (analogue of `ReportFormulaStage`,
 * reimplemented Job-native over batched channels). Appends one field per [formula] entry to every record:
 * each formula is an arbitrary Kotlin expression over the record's columns (referenced by name), compiled
 * via the genuine [CalculatedColumnEval] engine — **reused as shared infra, not reimplemented** (formula
 * evaluation is a Kotlin-script compile, not a "stage" to rewrite; this is why the pipe standardizes on
 * [tech.kzen.auto.plugin.model.record.FlatFileRecord] rather than `List<String>`). The engine is injected
 * as a `@Service`, exactly as `ReportDocument` obtains it.
 *
 * Formulas are compiled lazily and recompiled only when the incoming header changes (the engine's
 * `RecordHeaderIndex` maps column names -> positions per header). All formulas evaluate against the
 * ORIGINAL columns, then their values are appended together (matching Report — formulas cannot reference
 * each other's outputs). The incoming batch's records are mutated in place and forwarded under the
 * augmented header (ownership has transferred to this Worker, so in-place append is race-free).
 *
 * Compilation is heavy blocking work, so it runs through `control.runBlockingIo` to stay visible to
 * quiescence detection.
 */
@Reflect
class FormulaWorker(
    private val input: ChannelInput<Any?>,
    private val output: ChannelOutput<Any?>,

    private val formula: FormulaSpec,
    private val selfLocation: ObjectLocation,

    @Service private val calculatedColumnEval: CalculatedColumnEval
):
    Worker
{
    override suspend fun run(control: JobControl) {
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
        val formulaEntries = formula.formulas.entries.toList()
        val formulaNames = HeaderListing.ofUnique(formulaEntries.map { it.key })
        val formulaValues = Array(formulaEntries.size) { "" }

        // Compiled lazily; recompiled only when the incoming header changes (HeaderListing value-compare).
        var compiledForHeader: HeaderListing? = null
        var compiledColumns: List<CalculatedColumn<Any>> = listOf()
        var augmentedHeader: HeaderListing = HeaderListing.empty

        var computed = 0L
        try {
            for (item in input) {
                control.checkpoint()
                val batch = item as RecordBatch

                if (batch.header != compiledForHeader) {
                    compiledColumns = control.runBlockingIo {
                        formulaEntries.map { (name, expression) ->
                            calculatedColumnEval.create(
                                name, expression, batch.header, ClassNames.kotlinAny, classLoader)
                        }
                    }
                    augmentedHeader = batch.header.append(formulaNames)
                    compiledForHeader = batch.header
                }

                for (record in batch.records) {
                    for (i in compiledColumns.indices) {
                        formulaValues[i] = ColumnValue.toText(
                            compiledColumns[i].evaluate(Unit, record, batch.header))
                    }
                    record.addAll(formulaValues)
                    computed += 1
                }

                output.send(RecordBatch(augmentedHeader, batch.records))
                control.publishProgress(selfLocation, mapOf("computed" to computed))
            }
            control.publishProgress(selfLocation, mapOf("computed" to computed), force = true)
        }
        finally {
            output.close()
        }
    }
}
