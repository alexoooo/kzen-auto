package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.job.path.PathBinding
import tech.kzen.auto.common.objects.document.job.path.PathBindingError
import tech.kzen.auto.common.objects.document.job.path.PathBindingResult
import tech.kzen.auto.common.objects.document.job.path.PathProjectionSpec
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec.WriterColumn
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.ColumnProjection
import tech.kzen.auto.server.objects.job.value.ColumnProjectionDescriptor
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * A writer's column selection ([WriterColumnSpec]) applied to each value: `*` is the payload's column
 * projection, a path entry is one further column read through [PathBinding] / [PathRowEvaluator] (a metadata
 * field, or a nested scalar). The path columns bind against the incoming contract, re-bound only when it
 * changes (a dynamic lane); a path that unnests is an error, since a writer emits exactly one row per value.
 */
class WriterColumns(
    @PublishedApi internal val spec: WriterColumnSpec
) {
    /** One value flattened: the payload's projection when a `*` column needs it, and the path columns' texts. */
    class Row(
        val projection: ColumnProjection?,
        val pathValues: Array<String?>?
    )


    private val pathColumns = PathProjectionSpec(spec.pathEntries())
    private var boundFor: DataContract? = null
    private var binding: PathBindingResult? = null
    private var evaluator: PathRowEvaluator? = null


    /** True when every column is the payload's projection, so a writer can take its direct path. */
    val payloadFieldsOnly: Boolean = spec.isPayloadFieldsOnly()


    fun row(element: DataValue, control: JobControl): Row {
        val projection =
            if (spec.columns.isEmpty() || WriterColumn.PayloadFields in spec.columns) {
                JobDataValues.projection(element)
            }
            else {
                null
            }
        val bound = boundColumns(element, control)
        val pathValues = bound?.let { checkNotNull(evaluator).rows(element).single().values }
        return Row(projection, pathValues)
    }


    /** The column names of [row], each payload column rendered by [payloadName]. */
    fun header(row: Row, payloadName: (HeaderLabel) -> String): List<String> =
        names(payloadName, { checkNotNull(row.projection).header }, { checkNotNull(binding) })


    /**
     * The column names of every value of [contract], known before any value arrives: the names [header] gives
     * each of them. Fails by name ([tech.kzen.lib.common.exec.data.problem.DataException]) when the payload has no
     * static column projection.
     */
    fun header(contract: DataContract, payloadName: (HeaderLabel) -> String): List<String> =
        names(payloadName, { ColumnProjectionDescriptor.from(contract.payload()).header }, { bind(contract) })


    /** The path columns' texts of [element], bound against [contract]; null when there are no path columns. */
    fun pathValues(element: DataValue, contract: DataContract): Array<String?>? {
        if (pathColumns.isEmpty()) {
            return null
        }
        bindingFor(contract)
        return checkNotNull(evaluator).rows(element).single().values
    }


    /** Each field text of [row], in column order. */
    inline fun forEachField(row: Row, action: (String) -> Unit) {
        val projection = row.projection
        if (spec.columns.isEmpty()) {
            for (index in 0 until checkNotNull(projection).size) {
                action(projection.render(index))
            }
            return
        }
        var pathIndex = 0
        for (column in spec.columns) {
            when (column) {
                WriterColumn.PayloadFields -> for (index in 0 until checkNotNull(projection).size) {
                    action(projection.render(index))
                }
                is WriterColumn.Path -> action(checkNotNull(row.pathValues)[pathIndex++] ?: "")
            }
        }
    }


    fun fields(row: Row): List<String> {
        val fields = ArrayList<String>()
        forEachField(row) { fields += it }
        return fields
    }


    /** The binding error of the path columns against a known [contract], for the Worker's validation. */
    fun staticError(contract: DataContract): String? {
        if (pathColumns.isEmpty() || contract.structural is DataType.Dynamic) {
            return null
        }
        return bind(contract).errorMessage()?.let { "Columns: $it" }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun names(
        payloadName: (HeaderLabel) -> String,
        payloadHeader: () -> HeaderListing,
        paths: () -> PathBindingResult
    ): List<String> {
        if (spec.columns.isEmpty()) {
            return payloadHeader().values.map(payloadName)
        }
        val names = ArrayList<String>()
        var pathIndex = 0
        for (column in spec.columns) {
            when (column) {
                WriterColumn.PayloadFields -> names += payloadHeader().values.map(payloadName)
                is WriterColumn.Path -> names += paths().paths[pathIndex++].outputName
            }
        }
        return names
    }


    private fun boundColumns(element: DataValue, control: JobControl): PathBindingResult? {
        if (pathColumns.isEmpty()) {
            return null
        }
        val inputContract = control.inputContract()
            ?.takeUnless { it.structural is DataType.Dynamic }
            ?: element.contract
        return bindingFor(inputContract)
    }


    private fun bindingFor(contract: DataContract): PathBindingResult {
        val current = binding
        if (current != null && boundFor == contract) {
            return current
        }
        val bound = bind(contract)
        check(bound.isValid) { "Columns: ${bound.errorMessage()}" }
        boundFor = contract
        binding = bound
        evaluator = PathRowEvaluator(bound.paths)
        return bound
    }


    private fun bind(contract: DataContract): PathBindingResult {
        val bound = PathBinding.bind(pathColumns, contract)
        val unnesting = pathColumns.entries.filter { it.path.unnests }
        if (unnesting.isEmpty()) {
            return bound
        }
        return PathBindingResult(bound.paths, null, bound.errors + unnesting.map {
            PathBindingError(it.path, "a writer column cannot unnest; flatten the list with a Paths Worker first")
        })
    }
}
