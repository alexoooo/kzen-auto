package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
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
 * The calculated-column stage as a Job Worker (analogue of `ReportFormulaStage`, reimplemented Job-native over
 * batched channels). Appends one field per [formula] entry to every record: each formula is an arbitrary
 * Kotlin expression over the record's columns (referenced by name), compiled via the genuine
 * [CalculatedColumnEval] engine — **reused as shared infra, not reimplemented** — injected as a `@Service`,
 * exactly as `ReportDocument` obtains it. This is why the pipe standardizes on [FlatFileRecord] rather than
 * `List<String>` (see [DataRecord]).
 *
 * Formulas are compiled lazily and recompiled only when the incoming header changes. All formulas evaluate
 * against the ORIGINAL columns, then their values are appended together (matching Report — formulas cannot
 * reference each other's outputs). The incoming batch's records are mutated in place and forwarded under the
 * augmented header (ownership has transferred to this Worker, so in-place append is race-free).
 *
 * A [TransformWorker]: the framework owns the drain loop, per-batch checkpoint, throttled progress, and
 * end-of-stream close propagation. Compilation is heavy blocking work, so it runs through
 * [JobControl.runBlockingIo] to stay visible to quiescence detection.
 */
@Reflect
class FormulaWorker(
    input: ChannelInput<Any?>,
    output: ChannelOutput<Any?>,

    private val formula: FormulaSpec,
    selfLocation: ObjectLocation,

    @Service private val calculatedColumnEval: CalculatedColumnEval
):
    TransformWorker<DataRecord, DataRecord>(input, output, selfLocation)
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val formulaEntries = formula.formulas.entries.toList()
    private val formulaNames = HeaderListing.ofUnique(formulaEntries.map { it.key })
    private val formulaValues = Array(formulaEntries.size) { "" }

    // Compiled lazily; recompiled only when the incoming header changes (HeaderListing value-compare).
    private var compiledForHeader: HeaderListing? = null
    private var compiledColumns: List<CalculatedColumn<Any>> = listOf()
    private var augmentedHeader: HeaderListing = HeaderListing.empty

    private var computed = 0L


    override suspend fun onElement(element: DataRecord, emit: Emitter<DataRecord>, control: JobControl) {
        if (element.header != compiledForHeader) {
            compiledColumns = control.runBlockingIo {
                formulaEntries.map { (name, expression) ->
                    calculatedColumnEval.create(
                        name, expression, element.header, ClassNames.kotlinAny, classLoader)
                }
            }
            augmentedHeader = element.header.append(formulaNames)
            compiledForHeader = element.header
        }

        val record = element.record
        for (i in compiledColumns.indices) {
            formulaValues[i] = ColumnValue.toText(
                compiledColumns[i].evaluate(Unit, record, element.header))
        }
        record.addAll(formulaValues)
        computed += 1

        emit.send(DataRecord(augmentedHeader, record))
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("computed" to computed)
}
