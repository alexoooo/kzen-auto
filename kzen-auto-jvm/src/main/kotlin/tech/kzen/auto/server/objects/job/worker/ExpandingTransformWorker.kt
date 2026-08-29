package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * A transform whose one input element may expand into arbitrarily many output batches. Unlike [TransformWorker],
 * this base checkpoints during an element's expansion: it therefore owns and migrates the immutable physical
 * input batch, its next element index, and the empty-at-checkpoint [Emitter] cadence. A subclass owns only its
 * per-element cursor/state through [captureExpansionState] and [loadExpansionState].
 *
 * The index advances only after [onElement] returns. Every cadence checkpoint follows a completed output flush,
 * so replay adopts the active element and its subclass cursor without duplicating or losing output. The active
 * batch is frozen in migration state; it is never reconstructed from `JobChannel.drainBuffered`.
 */
abstract class ExpandingTransformWorker(
    private val input: ChannelInput<*>,
    private val output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
): WorkerBase(selfLocation) {
    private val emitter = Emitter(output)
    private var activeBatch: List<DataValue>? = null
    private var nextIndex = 0


    final override suspend fun drive(control: JobControl) {
        emitter.flushCadence(control) { publish(control) }
        while (true) {
            var batch = activeBatch
            if (batch == null) {
                control.checkpoint()
                val received = input.receiveBatch()
                    ?: break
                batch = received.map(::receiveValue)
                activeBatch = batch
                nextIndex = 0
            }

            while (nextIndex < batch.size) {
                onElement(batch[nextIndex], emitter, control)
                nextIndex += 1
            }

            activeBatch = null
            nextIndex = 0
            emitter.flush()
            publish(control)
        }

        onComplete(emitter, control)
        emitter.flush()
    }


    final override suspend fun onClose() {
        try {
            onExpansionClose()
        }
        finally {
            output.close()
        }
    }


    protected abstract suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl)


    protected open suspend fun onComplete(emit: Emitter, control: JobControl) {}


    /** Subclass resource cleanup; the base closes [output] afterwards even when this hook fails. */
    protected open fun onExpansionClose() {}


    /** Captures only subclass-owned state; the active physical batch/index are always captured by this base. */
    protected open fun captureExpansionState(): Any? = null


    /** Loads the subclass state previously returned by [captureExpansionState]. */
    protected open fun loadExpansionState(captured: Any?) {}


    final override fun captureMigrationState(): Any {
        return ExpansionState(activeBatch, nextIndex, captureExpansionState())
    }


    final override fun loadMigrationState(captured: Any?) {
        val state = captured as? ExpansionState
        if (state == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        activeBatch = state.activeBatch
        nextIndex = state.nextIndex
        loadExpansionState(state.adoptSubclassState())
    }


    private class ExpansionState(
        val activeBatch: List<DataValue>?,
        val nextIndex: Int,
        private var subclassState: Any?
    ): AutoCloseable {
        fun adoptSubclassState(): Any? {
            val adopted = subclassState
            subclassState = null
            return adopted
        }


        override fun close() {
            val closing = subclassState
            subclassState = null
            (closing as? AutoCloseable)?.close()
        }
    }
}
