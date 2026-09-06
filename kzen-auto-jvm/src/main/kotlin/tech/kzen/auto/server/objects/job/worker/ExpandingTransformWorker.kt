package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.channel.ReceivedBatch
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
 * batch is detached from the channel's capture and frozen in migration state; it is never reconstructed from
 * `JobChannel.drainBuffered`.
 *
 * OWNERSHIP (E9): the channel's hold on an owned element stays in place until that element's expansion has
 * completed (the batch travels with its leases across a migration, so a replacement instance resumes the
 * element still held), and the callback holds its own per-callback lease alongside; a batch a removed Worker
 * carried is released when the engine closes its orphaned state.
 */
abstract class ExpandingTransformWorker(
    private val input: ChannelInput<*>,
    private val output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
): WorkerBase(selfLocation) {
    private val emitter = Emitter(output)
    private var activeBatch: ReceivedBatch? = null
    private var nextIndex = 0


    final override suspend fun drive(control: JobControl) {
        emitter.flushCadence(control) { publish(control) }
        while (true) {
            var batch = activeBatch
            if (batch == null) {
                control.checkpoint()
                val received = ReceivedBatch.receive(input, ::receiveValue)
                    ?: break
                // This Worker carries the batch across checkpoints itself; the channel must not capture it too
                received.detach()
                batch = received
                activeBatch = batch
                nextIndex = 0
            }

            while (nextIndex < batch.size) {
                val index = nextIndex
                val element = batch.elements[index]
                CallbackLeases.holding(control, element) {
                    onElement(element, emitter, control)
                }
                // The element's expansion is complete: advance first (a close failure below must not replay it),
                // then let the channel's hold go
                nextIndex = index + 1
                batch.markDispatched(index)
                batch.channelLease(index)?.release()
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
        activeBatch = state.adoptBatch()
        nextIndex = state.nextIndex
        loadExpansionState(state.adoptSubclassState())
    }


    private class ExpansionState(
        private var activeBatch: ReceivedBatch?,
        val nextIndex: Int,
        private var subclassState: Any?
    ): AutoCloseable {
        fun adoptBatch(): ReceivedBatch? {
            val adopted = activeBatch
            activeBatch = null
            return adopted
        }


        fun adoptSubclassState(): Any? {
            val adopted = subclassState
            subclassState = null
            return adopted
        }


        override fun close() {
            val closing = subclassState
            subclassState = null
            val dropped = activeBatch
            activeBatch = null
            try {
                (closing as? AutoCloseable)?.close()
            }
            finally {
                // The carried elements are dropped with the Worker: their channel holds go with them
                dropped?.releaseRemaining()
            }
        }
    }
}
