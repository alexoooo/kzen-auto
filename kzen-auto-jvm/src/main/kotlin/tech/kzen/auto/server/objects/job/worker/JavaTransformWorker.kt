package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.reflect.full.createType


/**
 * A transform Worker written without coroutines: the subclass sees each input element as an ordinary object
 * ([JobDataValues.boundary] — the native object a lifted value carries, or a map/list/scalar snapshot) and
 * answers with an `Iterator` of output objects, or null for none; [onCompleteBlocking] may add trailing
 * outputs. The framework runs each callback through [JobControl.runBlockingIo], lifts and emits every output,
 * and closes a returned iterator that is [AutoCloseable] once it is drained. Stateful analysis (a per-symbol
 * book across a day) lives in the subclass's fields, which is what live-edit migration preserves when the
 * subclass overrides the capture/load hooks of [WorkerBase].
 */
abstract class JavaTransformWorker @JvmOverloads constructor(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation,
    serve: ChannelServer<Any?, Any?>? = null
):
    TransformWorker(input, output, selfLocation, serve)
{
    /** Outputs for one input element, or null. */
    protected abstract fun onElementBlocking(element: Any?, control: JobControl): Iterator<*>?

    /** Trailing outputs once the input stream has ended, or null. */
    protected open fun onCompleteBlocking(control: JobControl): Iterator<*>? = null

    /**
     * The output element contract, when statically known; null lets each element describe itself. A declared
     * contract is also what the design-time walk shows on the card and hands downstream ([payloadFlow]).
     */
    protected open fun outputContract(): DataContract? = null

    /**
     * The output element class, when the outputs are plain objects of one type (a record, a bean, an enum) —
     * described through the same registry that lifts them at run time, so the card shows the class's shape
     * before any run. [outputContract] wins when both are given; null means each element describes itself.
     */
    protected open fun outputClass(): Class<*>? = null

    /**
     * True when every output is a deliberate copy that holds nothing reachable from the element it came from —
     * a row of scalars read off a native model, a summary. By default a non-scalar output inherits its element's
     * owners (E9 item 3: an output derived from an element may hold anything reachable from it), so an
     * accumulator downstream (a Sort) that keeps the rows keeps the natives too; a Worker that knows its rows
     * are copies declares it here, its element closes when the callback returns, and only the rows travel.
     * Declaring this for an output that does reference the element is a use-after-close waiting to happen.
     */
    protected open fun independentOutputs(): Boolean = false


    final override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt =
        staticOutputContract()?.let { JobLaneAttempt(JobLaneDescriptor(it), null) }
            ?: super.payloadFlow(input, context)


    private fun staticOutputContract(): DataContract? =
        outputContract() ?: outputClass()?.let { JobDataValues.describe(it.kotlin.createType()) }


    final override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val native = JobDataValues.boundary(element)
        val outputs = control.runBlockingIo { onElementBlocking(native, control) }
        emitAll(outputs, emit, element, control)
    }


    final override suspend fun onComplete(emit: Emitter, control: JobControl) {
        val outputs = control.runBlockingIo { onCompleteBlocking(control) }
        emitAll(outputs, emit, null, control)
    }


    // An output derived from an element may hold anything reachable from it, so a non-scalar output inherits
    // the element's owners (E9 item 3) unless the subclass declares its outputs independent copies; a closeable
    // the callback constructed is adopted when it is sent either way.
    private suspend fun emitAll(outputs: Iterator<*>?, emit: Emitter, element: DataValue?, control: JobControl) {
        if (outputs == null) {
            return
        }
        val inheriting = element != null && !independentOutputs()
        try {
            while (outputs.hasNext()) {
                val output = JobDataValues.lift(outputs.next(), staticOutputContract())
                if (inheriting) {
                    control.ownership()?.inherit(output, element!!)
                }
                emit.send(output)
            }
        }
        finally {
            (outputs as? AutoCloseable)?.close()
        }
    }
}
