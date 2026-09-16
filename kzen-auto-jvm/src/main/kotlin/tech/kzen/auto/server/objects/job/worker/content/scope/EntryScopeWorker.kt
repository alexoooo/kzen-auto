package tech.kzen.auto.server.objects.job.worker.content.scope

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.exec.job.ownership.LeaseHolder
import tech.kzen.auto.server.exec.job.ownership.OwnedNative
import tech.kzen.auto.server.exec.job.ownership.RunOwnershipLedger
import tech.kzen.auto.server.objects.job.channel.DownstreamClosedException
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.CallbackLeases
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.OpenedStream
import tech.kzen.auto.server.objects.job.worker.SourceIngress
import tech.kzen.auto.server.objects.job.worker.WorkerBase
import tech.kzen.auto.server.objects.job.worker.content.Entry
import tech.kzen.auto.server.objects.job.worker.content.tar.TarEntryContent
import tech.kzen.auto.server.objects.job.worker.content.tar.TarGzEntryCursor
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.auto.server.objects.job.worker.ownership
import tech.kzen.auto.server.objects.job.worker.toFilePath
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import kotlin.reflect.full.createType


/**
 * The explicit scope of design §6 over a `.tar.gz` (spike, docs/plans/2026-09-16_borrowed-elements.md
 * CS1): owns one [TarGzEntryCursor] for the run, and for each header-selected entry lifts an [Entry] whose
 * content is borrowed from the cursor, re-enters the inline body with it, waits for every body Worker's
 * `onEntryEnd`, verifies nothing still holds the entry, releases it, and only then advances the cursor. Only
 * what the last body Worker emits leaves the scope, through [output], and it must be independent of the
 * borrowed entry (§6.3): a value still carrying the entry's owner fails by name.
 *
 * Quiescence (CS3): the scope parks at the entry boundary ONLY. Inside an entry every read and the cursor
 * advance run through [JobControl.runBlockingIo], rows leaving mid-entry are flushed at the channel's batch
 * size without a checkpoint, and any checkpoint the body does make there (a reader's per-batch one) is skipped
 * by the control while the scope is [insideEntry] (see [ScopeBoundary]); the run's consumers downstream of the
 * scope keep draining meanwhile, so a pause lands after the entry the scope was on. Body Workers
 * are the nested objects under `body` (a `NestedList`, injected as locations), resolved against the run graph
 * in [loadDefinitionContext] and chained in document order.
 *
 * Live edit (CS3, §6.7): at the boundary the open cursor is detached with the scope's counters and each body
 * Worker's carried state ([captureMigrationState]) and adopted by the rebuilt scope ([loadMigrationState]),
 * whose body Workers come from the edited notation and are started once before they adopt their state. An
 * edited `entries:` applies from the next header; the cursor never rewinds and the archive is never
 * re-opened. Compatibility (`path`) is validated from notation before anything is detached
 * ([ScopeMigrationKey]); the check here is the last line of defence. A capture taken inside an entry (the
 * run quiesced there by something other than a pause, a downstream breakpoint say) cannot be adopted:
 * mid-entry adoption is not built, and the rebuilt scope fails by name rather than re-read the archive.
 *
 * Early completion (CS2): a [DownstreamClosedException] from the output channel (its consumer completed) or
 * from a body Worker (a `Take` in the body) ends the active entry in order, stops the loop, closes the archive
 * cursor without draining it, still runs every body Worker's `onComplete` (its emits are dropped) and settles
 * the Worker normally.
 */
@Reflect
class EntryScopeWorker(
    private val path: String,
    entries: List<String>,
    body: List<ObjectLocation>,
    private val output: ChannelOutput<DataValue>,
    private val selfLocation: ObjectLocation
):
    WorkerBase(selfLocation), ScopeBoundary
{
    //-----------------------------------------------------------------------------------------------------------------
    private val bodyLocations = body
    private val select = EntryGlob(entries)
    private val emitter = Emitter(output)
    private val scopeHolder = LeaseHolder(selfLocation.asString() + "#scope")
    private val entryContract: DataContract = JobDataValues.describe(Entry::class.createType())

    private var bodyWorkers: List<ScopeBodyWorker> = emptyList()
    private var stream: OpenedStream? = null
    private var restored: DetachedScope? = null

    private var delivered = 0L
    private var skipped = 0L
    private var emitted = 0L
    private var sinceFlush = 0
    private var activeOwner: OwnedNative? = null

    // The entry the body is on, read by downstream Workers' coroutines (ScopeBoundary)
    @Volatile
    private var activeEntry: String? = null

    // Set once whatever is downstream of the scope's output (a completed channel consumer) or of a body Worker
    // (a Take inside the body) has completed: the scope stops reading.
    private var downstreamClosed = false

    // Set once the OUTPUT channel itself refused a send (its consumer completed): from then on nothing can
    // leave, so late emits are dropped. A closure raised inside the body leaves the output open, and what the
    // body sent before closing is still flushed to it.
    private var outputClosed = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun loadDefinitionContext(context: WorkerDefinitionContext) {
        bodyWorkers = bodyLocations.map { location ->
            when (val resolution = context.resolve(location.toReference(), selfLocation)) {
                is WorkerDefinitionResolution.Resolved ->
                    resolution.value as? ScopeBodyWorker
                        ?: throw IllegalStateException(
                            "Body of $selfLocation must hold scope body Workers; $location is " +
                                    resolution.value.javaClass.name)

                is WorkerDefinitionResolution.Failed ->
                    throw IllegalStateException(resolution.message)
            }
        }
    }


    override fun insideEntry(): Boolean =
        activeEntry != null


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun drive(control: JobControl) {
        check(bodyLocations.size == bodyWorkers.size) {
            "Body of $selfLocation was not resolved (${bodyWorkers.size} of ${bodyLocations.size})"
        }
        val ledger = control.ownership()
        val ingress = SourceIngress(control, selfLocation)
        val chain = chain(ledger, control)
        try {
            control.checkpoint()
            val opened = restored?.adopt()
                ?: ingress.openStream { TarGzEntryCursor(toFilePath(path), select::matches) }
                ?: throw IllegalStateException("Unable to open archive: $path")
            stream = opened
            val cursor = opened.iterator as TarGzEntryCursor
            // An adopted cursor selected under the previous notation's `entries`; this scope's apply from the
            // next header
            cursor.reselect(select::matches)

            for (worker in bodyWorkers) {
                worker.onStart(control)
            }
            restored?.let { adoptCarried(it) }
            restored = null

            while (!downstreamClosed) {
                control.checkpoint()
                val entry = control.runBlockingIo {
                    if (cursor.hasNext()) cursor.next() else null
                } ?: break
                skipped = cursor.skipped()
                delivered += 1
                try {
                    deliver(entry, ledger, chain, control)
                }
                catch (e: DownstreamClosedException) {
                    // Downstream is done (a completed consumer of the output, or a Take in the body): the active
                    // entry was ended and released by deliver; the cursor closes below WITHOUT draining the rest
                    downstreamClosed = true
                }
                flushOutput()
                publish(control)
            }
            skipped = cursor.skipped()

            for ((index, worker) in bodyWorkers.withIndex()) {
                try {
                    worker.onComplete(chain[index], control)
                }
                catch (e: DownstreamClosedException) {
                    downstreamClosed = true
                }
            }
            flushOutput()
            publish(control, force = true)
        }
        finally {
            try {
                closeStream()
            }
            finally {
                output.close()
            }
        }
    }


    private fun closeStream() {
        val open = stream ?: return
        stream = null
        open.close()
    }


    override suspend fun onClose() {
        closeStream()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Migration (CS3): the open cursor and the counters move to the rebuilt scope as a live resource; onClose
    // then skips the cursor here because the field was cleared by the capture. Body state is keyed by body
    // location so an edit that adds, removes or reorders body Workers lines up what survives.
    override fun captureMigrationState(): Any? {
        val open = stream ?: return null
        stream = null
        val bodyStates = LinkedHashMap<ObjectLocation, Any?>()
        for ((index, worker) in bodyWorkers.withIndex()) {
            worker.captureState()?.let { bodyStates[bodyLocations[index]] = it }
        }
        return DetachedScope(open, delivered, skipped, emitted, activeEntry, bodyStates, path)
    }


    override fun loadMigrationState(captured: Any?) {
        val detached = captured as? DetachedScope
        if (detached == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        if (detached.path != path) {
            detached.close()
            error("Source selection changed. Start a new run to apply it.")
        }
        val interrupted = detached.interruptedEntry
        if (interrupted != null) {
            detached.close()
            error("Scope was interrupted inside entry '$interrupted'; a live edit applies only at an entry " +
                    "boundary. Start a new run to apply it.")
        }
        restored = detached
    }


    private fun adoptCarried(detached: DetachedScope) {
        delivered = detached.delivered
        skipped = detached.skipped
        emitted = detached.emitted
        for ((index, worker) in bodyWorkers.withIndex()) {
            detached.bodyStates[bodyLocations[index]]?.let { worker.loadState(it) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** One entry's lifecycle (design §6.1): offered to the body, ended, checked for holds, released. */
    private suspend fun deliver(
        entry: Entry,
        ledger: RunOwnershipLedger?,
        chain: List<BodyEmitter>,
        control: JobControl
    ) {
        val content = entry.content as TarEntryContent
        val release = EntryRelease(entry.name, content)
        val value = JobDataValues.lift(entry, entryContract)

        val authority = ledger?.let {
            val adoption = it.adopt(release, scopeHolder)
            it.attach(value, adoption.owners)
            adoption.producerLease
        }
        val owned = ledger?.entryOf(release)
        activeOwner = owned
        activeEntry = entry.name

        try {
            var closedDuringEntry: DownstreamClosedException? = null
            try {
                if (bodyWorkers.isEmpty()) {
                    chain.last().send(value)
                }
                else {
                    CallbackLeases.holding(control, value) {
                        bodyWorkers.first().onElement(value, chain.first(), control)
                    }
                }
            }
            catch (e: DownstreamClosedException) {
                // Not a failure: the entry still ends in order (every body's onEntryEnd, the hold check), and
                // only then does the closure reach the drive loop
                closedDuringEntry = e
            }
            for (worker in bodyWorkers) {
                worker.onEntryEnd(control)
            }
            if (owned != null) {
                val holders = owned.holds().filterKeys { it != scopeHolder }
                check(holders.isEmpty()) {
                    "Entry '${entry.name}' is still held after its scope ended: " +
                            holders.keys.joinToString { it.name }
                }
            }
            if (closedDuringEntry != null) {
                throw closedDuringEntry
            }
        }
        finally {
            activeEntry = null
            activeOwner = null
            if (authority != null) {
                authority.release()
            }
            else {
                release.close()
            }
        }
    }


    /** The chain of emitters: body Worker `i` sends into body Worker `i + 1`; the last sends out of the scope. */
    private fun chain(ledger: RunOwnershipLedger?, control: JobControl): List<BodyEmitter> {
        val out = BodyEmitter { element -> leave(element, ledger) }
        if (bodyWorkers.isEmpty()) {
            return listOf(out)
        }
        val emitters = ArrayList<BodyEmitter>(bodyWorkers.size)
        var next: BodyEmitter = out
        for (index in bodyWorkers.size - 1 downTo 0) {
            emitters.add(next)
            if (index > 0) {
                val worker = bodyWorkers[index]
                val downstream = next
                next = BodyEmitter { element -> worker.onElement(element, downstream, control) }
            }
        }
        return emitters.reversed()
    }


    /** The scope boundary (§6.3): an element leaving must not depend on the borrowed entry. */
    private suspend fun leave(element: DataValue, ledger: RunOwnershipLedger?) {
        if (outputClosed) {
            // A late emit after the output's consumer has gone: nowhere to send it
            return
        }
        val owner = activeOwner
        if (ledger != null && owner != null && owner in ledger.owners(element)) {
            throw IllegalStateException(
                "Output of $selfLocation depends on the borrowed entry '$activeEntry'; " +
                        "only values independent of the entry may leave the scope")
        }
        emitted += 1
        try {
            emitter.send(element)
        }
        catch (e: DownstreamClosedException) {
            outputClosed = true
            throw e
        }
        // Rows leaving mid-entry (a ReadPart body over a large entry) reach the channel at its batch size,
        // without the checkpoint a framework cadence would add: the scope quiesces at entry boundaries only
        sinceFlush += 1
        if (sinceFlush >= emitter.batchSize()) {
            flushOutput()
        }
    }


    /** Flushes what the body sent out unless the output's consumer is known to be gone. */
    private suspend fun flushOutput() {
        sinceFlush = 0
        if (outputClosed) {
            return
        }
        try {
            emitter.flush()
        }
        catch (e: DownstreamClosedException) {
            outputClosed = true
            downstreamClosed = true
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf(
            "entries" to delivered,
            "skipped" to skipped,
            "emitted" to emitted,
            "closed" to downstreamClosed,
            "entry" to activeEntry)


    /** The scope's authority over one entry (an owned native keyed in the ledger): closing invalidates the content. */
    private class EntryRelease(
        val name: String,
        private val content: TarEntryContent
    ): AutoCloseable {
        override fun close() {
            content.invalidate()
        }

        override fun toString(): String = "EntryRelease($name)"
    }


    /** What moves across a live edit: the open cursor, the counters, each body's carried state, the key. */
    private class DetachedScope(
        private var stream: OpenedStream?,
        val delivered: Long,
        val skipped: Long,
        val emitted: Long,
        val interruptedEntry: String?,
        val bodyStates: Map<ObjectLocation, Any?>,
        val path: String
    ): AutoCloseable {
        fun adopt(): OpenedStream {
            val adopted = stream ?: throw IllegalStateException("Detached scope already adopted or closed")
            stream = null
            return adopted
        }

        override fun close() {
            val open = stream ?: return
            stream = null
            try {
                bodyStates.values.forEach { (it as? AutoCloseable)?.close() }
            }
            finally {
                open.close()
            }
        }
    }
}
