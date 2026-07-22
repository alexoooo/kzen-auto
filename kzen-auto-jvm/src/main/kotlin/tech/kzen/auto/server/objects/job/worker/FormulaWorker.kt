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
 * batched channels). Appends one flat-part field per [formula] entry to every message: each formula is an
 * arbitrary Kotlin expression over the flat part's columns (referenced by name), compiled via the genuine
 * [CalculatedColumnEval] engine — **reused as shared infra, not reimplemented** — injected as a `@Service`,
 * exactly as `ReportDocument` obtains it. This is why the flat part standardizes on
 * [tech.kzen.auto.plugin.model.record.FlatFileRecord] rather than `List<String>` (see [JobMessage]).
 *
 * Formulas are compiled lazily and recompiled only when the incoming header changes. The Job's declared
 * parameters are in every formula's scope, bare by name and typed, their run-constant values read via
 * [JobControl.parameter]. All formulas evaluate against the ORIGINAL columns, then their values are appended
 * together (matching Report — formulas cannot reference each other's outputs). The incoming message is mutated in place — the flat record grows the
 * computed fields and the view's header reference swaps to the augmented one — and forwarded with its
 * payload untouched (ownership has transferred to this Worker, so in-place append is race-free). A
 * payload-lane message auto-flattens first ([JobMessage.flatView]), so formulas over a scalar stream see a
 * `value` column; transforming the PAYLOAD itself is the element-model plan's phase 3 (`payload:` expression).
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
    TransformWorker(input, output, selfLocation)
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


    override suspend fun onElement(element: JobMessage, emit: Emitter, control: JobControl) {
        val flat = element.flatView()
        val header = flat.header
        if (header != compiledForHeader) {
            // The Job's declared parameters join every formula's scope, bare by name and typed; their values are
            // run-constant, injected once per compiled instance (never baked into the generated source).
            val parameters = control.parameters()
            val parameterValues = parameters.components.map { control.parameter(it.name.value) }
            compiledColumns = control.runBlockingIo {
                formulaEntries.map { (name, expression) ->
                    calculatedColumnEval.create(
                        name, expression, header, ClassNames.kotlinAny, classLoader, parameters)
                }
            }
            compiledColumns.forEach { it.setParameters(parameterValues) }
            augmentedHeader = header.append(formulaNames)
            compiledForHeader = header
        }

        val record = flat.record
        for (i in compiledColumns.indices) {
            formulaValues[i] = ColumnValue.toText(
                compiledColumns[i].evaluate(Unit, record, header))
        }
        record.addAll(formulaValues)
        computed += 1

        flat.header = augmentedHeader
        emit.send(element)
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("computed" to computed)
}
