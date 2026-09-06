package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.path.PathBinding
import tech.kzen.auto.common.objects.document.job.path.PathBindingResult
import tech.kzen.auto.common.objects.document.job.path.PathProjectionSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * The object-graph PROJECTION / UNNEST transform (E8 item 1): notation lists [paths] over the incoming record
 * contract (`instrument.symbol`, `executions[*].price`, `attributes[*].value.price`) and every input element
 * becomes flat rows of those leaves — one row per unnested element, the other columns repeated; paths on the
 * same list share one iteration, paths on different lists form a cross product. The rules a row follows (a
 * null intermediate keeps the row with nulls, an empty list yields no rows, a map's `[*]` exposes `key` /
 * `value` in entries order) live in [PathRowEvaluator]; the binding rules (fields must exist, `[*]` needs a
 * list or map, leaves must be scalar, output names — the dotted path with wildcards dropped, or the alias —
 * must be unique) in [PathBinding], shared with the design-time picker.
 *
 * The output is a fresh flat record of nullable scalars (`JobDataValues.projectedRecord`): every leaf is
 * copied as text inside the callback, so a row never aliases the element's native storage — an owned element
 * (E9) is read while the callback holds it and closes when the framework lets it go; the rows outlive it and
 * inherit no owner. The output contract is known statically from the upstream contract ([payloadFlow]) and
 * re-bound at run time only when the element contract differs from the static one (a dynamic lane).
 */
@Reflect
class PathProjectionWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val paths: PathProjectionSpec,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation)
{
    private var boundFor: DataContract? = null
    private var binding: PathBindingResult? = null
    private var evaluator: PathRowEvaluator? = null
    private var rows = 0L
    private var elements = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val inputContract = control.inputContract()
            ?.takeUnless { it.structural is DataType.Dynamic }
            ?: element.contract
        val bound = bindingFor(inputContract)
        val outputContract = checkNotNull(bound.contract)
        val rowsOfElement = checkNotNull(evaluator).rows(element)
        elements += 1
        for (row in rowsOfElement) {
            rows += 1
            emit.send(JobDataValues.projectedRecord(outputContract, FlatFileRecord.of(row.texts()), row.states()))
        }
    }


    private fun bindingFor(inputContract: DataContract): PathBindingResult {
        val current = binding
        if (current != null && boundFor == inputContract) {
            return current
        }
        val bound = PathBinding.bind(paths, inputContract)
        check(bound.isValid) { "Path projection: ${bound.errorMessage()}" }
        boundFor = inputContract
        binding = bound
        evaluator = PathRowEvaluator(bound.paths)
        return bound
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The static walk: the flat record of the paths' leaves, or the binding errors as this Worker's validation
    // error (surfaced on its card, never a run-time crash). A dynamic upstream binds at run time instead.
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        if (paths.isEmpty()) {
            return JobLaneAttempt(JobLaneDescriptor.unknown, "No paths configured")
        }
        if (input.contract.structural is DataType.Dynamic) {
            return JobLaneAttempt(JobLaneDescriptor.unknown, null)
        }
        val bound = PathBinding.bind(paths, input.contract)
        val contract = bound.contract
            ?: return JobLaneAttempt(JobLaneDescriptor.unknown, bound.errorMessage())
        return JobLaneAttempt(JobLaneDescriptor(contract), null)
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("elements" to elements, "rows" to rows)
}
