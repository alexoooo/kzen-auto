package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.exec.job.ownership.OwnedNative
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * The lending loop over one cursor (docs/plans/2026-09-16_borrowed-elements.md §3.2), shared by every Worker that
 * hands out elements valid only until it advances — a [CursorSourceWorker] over its run-long cursor, an
 * [tech.kzen.auto.server.objects.job.worker.content.ExtractWorker] over one archive per input element. The
 * framework owns every pull: the cursor and each pulled item are adopted by the run inside the blocking body
 * ([SourceIngress]), a [LentElement] is sent alone and the loop then suspends until its last hold is released
 * (its close), checkpoints at that boundary, and only then pulls the next. A closed downstream
 * ([tech.kzen.auto.server.objects.job.channel.DownstreamClosedException] from the send) leaves [drain] through
 * the exception with the cursor closed and nothing more read; the caller decides what a closed downstream
 * means for it.
 *
 * Live edit: [capture] detaches the open cursor with the items pulled ahead as a [DetachedCursor] the rebuilt
 * instance [adopt]s (no re-open, no skip); adoption is refused by name when the cursor's configuration key
 * differs, or when the capture was taken while an element was lent (impossible under the draining rule of
 * [BorrowingSource]; the belt-and-braces guard).
 */
internal class CursorLending(
    private val selfLocation: ObjectLocation
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var stream: OpenedStream? = null
    private var pending: ArrayDeque<AcquiredItem> = ArrayDeque()
    private var delivered = 0L

    // The lent element in flight, read from other Workers' coroutines and the deadlock monitor's thread
    @Volatile private var lent: OwnedNative? = null
    @Volatile private var lentName: String? = null
    @Volatile private var awaiting = false


    //-----------------------------------------------------------------------------------------------------------------
    /** True from the emit of a lent element until its release. */
    fun lending(): Boolean =
        lent?.isClosed == false


    /** True while suspended waiting for the lent element's release. */
    fun awaitingRelease(): Boolean =
        awaiting


    /** Elements delivered downstream so far, across every cursor drained by this instance. */
    fun delivered(): Long = delivered


    /** Restores the delivered count on a rebuilt instance that adopted no open cursor. */
    fun restoreDelivered(count: Long) {
        delivered = count
    }


    /** The lent element in flight, by name, or null between lent elements. */
    fun lentElementName(): String? = lentName


    /** Whether a cursor is open (or adopted) and not yet drained. */
    fun isOpen(): Boolean = stream != null


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Drains the open cursor — adopted, or opened now through [open] — lending each item; [onReady] sees the
     * iterator before the first pull; [metadataOf] gives each item's metadata. Returns at the end of the cursor; a
     * closed downstream propagates as [tech.kzen.auto.server.objects.job.channel.DownstreamClosedException]. The
     * cursor is closed on every exit.
     */
    suspend fun drain(
        control: JobControl,
        emit: Emitter,
        contract: DataContract?,
        open: () -> Iterator<*>,
        metadataOf: (Any?) -> ValueMetadata? = { null },
        onReady: (Iterator<*>) -> Unit
    ) {
        val ingress = SourceIngress(control, selfLocation)
        val opened = stream
            ?: (ingress.openStream { open() } ?: throw IllegalStateException("open returned no iterator"))
                .also { stream = it }
        try {
            onReady(opened.iterator)
            while (true) {
                if (pending.isEmpty()) {
                    val pulled = ingress.pull(opened.iterator, emit.batchSize())
                    if (pulled.isEmpty()) {
                        break
                    }
                    pending.addAll(pulled)
                }

                // Claimed before the send: a send parked mid-flush holds the value in the channel's in-flight
                // batch, which a migration carries — the resumed instance must not deliver it again
                val item = pending.removeFirst()
                delivered += 1
                val lentItem = item.native as? LentElement
                val owned = lentItem?.let { ingress.ledger()?.entryOf(it) }
                if (owned != null) {
                    lent = owned
                    lentName = lentItem.lentName()
                }
                try {
                    emit.send(item.lift(ingress.ledger(), contract, metadataOf(item.native)))
                }
                finally {
                    item.release()
                }
                if (owned != null) {
                    awaitRelease(owned, control)
                }
            }
        }
        finally {
            lent = null
            lentName = null
            close()
        }
    }


    /** The wait of §3.2 step 3: outside any checkpoint, then a checkpoint at the boundary before the next pull. */
    private suspend fun awaitRelease(owned: OwnedNative, control: JobControl) {
        awaiting = true
        try {
            owned.awaitClosed()
        }
        finally {
            awaiting = false
        }
        lent = null
        lentName = null
        control.checkpoint()
    }


    /** Lets go of the open cursor and any items pulled ahead; idempotent. */
    fun close() {
        val open = stream ?: return
        stream = null
        val undelivered = pending
        pending = ArrayDeque()
        try {
            undelivered.forEach { it.release() }
        }
        finally {
            open.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Detaches the open cursor and the items pulled ahead as live resources for the rebuilt instance; null when
     * no cursor is open. The fields are cleared, so a later [close] here skips them.
     */
    fun capture(configurationKey: Any?): Any? {
        val open = stream ?: return null
        stream = null
        val carried = pending
        pending = ArrayDeque()
        return DetachedCursor(open, carried, delivered, configurationKey, lentName)
    }


    /** Adopts a [capture]d cursor; anything else closeable is closed. Refuses by name (see class doc). */
    fun adopt(captured: Any?, configurationKey: Any?) {
        val detached = captured as? DetachedCursor
        if (detached == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        if (detached.configurationKey != configurationKey) {
            detached.close()
            error("Source selection changed. Start a new run to apply it.")
        }
        val interrupted = detached.lentName
        if (interrupted != null) {
            // Impossible while downstream draining holds (BorrowingSource); the guard against a capture taken
            // mid-element: the lent element is still someone's, and the cursor cannot advance under it. The
            // cursor is NOT closed here — the run's ledger owns it and closes it at teardown — so the holder
            // does not see its element invalidated first and report a released entry instead of this refusal
            error("Source was interrupted inside $interrupted; a live edit applies only between elements. " +
                    "Start a new run to apply it.")
        }
        val adopted = detached.adopt()
        stream = adopted.first
        pending = adopted.second
        delivered = detached.delivered
    }


    private class DetachedCursor(
        private var stream: OpenedStream?,
        private var pending: ArrayDeque<AcquiredItem>?,
        val delivered: Long,
        val configurationKey: Any?,
        val lentName: String?
    ): AutoCloseable {
        fun adopt(): Pair<OpenedStream, ArrayDeque<AcquiredItem>> {
            val adopted = stream ?: throw IllegalStateException("Detached cursor already adopted or closed")
            val items = pending ?: ArrayDeque()
            stream = null
            pending = null
            return adopted to items
        }


        override fun close() {
            val open = stream ?: return
            val items = pending ?: ArrayDeque()
            stream = null
            pending = null
            try {
                items.forEach { it.release() }
            }
            finally {
                open.close()
            }
        }
    }
}
