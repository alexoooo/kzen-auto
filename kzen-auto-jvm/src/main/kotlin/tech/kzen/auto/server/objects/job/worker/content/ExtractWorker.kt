package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.BorrowingSource
import tech.kzen.auto.server.objects.job.worker.CursorLending
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.ExpandingTransformWorker
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.content.tar.TarGzEntryCursor
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassName
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.full.createType


/**
 * Opens each incoming file as a `.tar.gz` and lends its members downstream, one [Entry] at a time
 * (docs/plans/2026-09-16_borrowed-elements.md §3.2, §3.5; the "Entries" transform of the 2026-09-15 content
 * analysis §7): the file selection is the `File` (or `Read`) source's, emitting whole files (`emit: units`) as
 * [DataUnit]s, so one file selector serves every downstream purpose. An [Entry] input is an archive inside an
 * enclosing archive — `Extract → Extract` unpacks nested archives — read from the entry's borrowed content
 * while the enclosing member is held by this callback.
 *
 * Each member is a lent element ([CursorLending]): it is valid only until the cursor advances, so the next is
 * read only once the member's last downstream hold is released; a Worker that would keep it past its callback
 * is refused by name, and a value derived from it that carries no owner (a `Written` record, a row) leaves
 * freely. `${parent.name}` in `Write` is the archive the member came from. [members] selects members by glob
 * at the header (empty selects every file member); a non-matching member costs one skip.
 *
 * Live edit: the open archive moves to the rebuilt instance between members (the base carries the input
 * element; the cursor is this Worker's expansion state); an edited `members:` applies from the next header
 * ([TarGzEntryCursor.reselect]), the cursor never rewinds and the archive is never re-opened. A change of the
 * file selection is refused upstream, by the source's own migration key.
 */
@Reflect
class ExtractWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    members: List<String>,
    selfLocation: ObjectLocation
):
    ExpandingTransformWorker(input, output, selfLocation), BorrowingSource
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val dataUnitClassName = ClassName(DataUnit::class.qualifiedName!!)
        private val entryClassName = ClassName(Entry::class.qualifiedName!!)

        private const val archivesKey = "archives"
        private const val archiveKey = "archive"
        private const val entriesKey = "entries"
        private const val skippedKey = "skipped"
        private const val entryKey = "entry"

        private const val laneRequirement =
            "Extract needs whole files: a File or Read source with Emit set to Units, or an Entry of an " +
                "enclosing archive; found "
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val select = EntryGlob(members)
    private val entryContract: DataContract = JobDataValues.describe(Entry::class.createType())
    private val lending = CursorLending(selfLocation)

    @Volatile
    private var cursor: TarGzEntryCursor? = null

    private var archives = 0L
    private var skippedBefore = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val open = opener(JobDataValues.native(element))
        try {
            lending.drain(control, emit, entryContract, open) { iterator ->
                val opened = iterator as TarGzEntryCursor
                // An adopted cursor selected under the previous notation's `members`; this instance's apply from
                // the next header
                opened.reselect(select::matches)
                cursor = opened
            }
        }
        finally {
            cursor?.let { skippedBefore += it.skipped() }
            cursor = null
        }
        archives += 1
    }


    private fun opener(native: Any?): () -> Iterator<*> {
        return when (native) {
            is Entry -> {
                val parent = ContentDescriptor(native.name, native.size, native.modifiedEpochMillis)
                ({ TarGzEntryCursor(
                    parent, SequentialByteContentInputStream(native.content.open()), select::matches) })
            }

            is DataUnit -> {
                val path = archivePath(native)
                ({ TarGzEntryCursor(path, select::matches) })
            }

            // Reached when the upstream lane was unknown statically (a File in Items mode over an undeclared
            // file), so the hint the validator would have given is the run-time refusal
            else -> throw IllegalStateException(
                laneRequirement + (native?.let { "${it::class.qualifiedName}: $it" } ?: "null"))
        }
    }


    private fun archivePath(unit: DataUnit): Path {
        val ref = unit.part(DataRole.main).ref
        val location = ref.asLocationOrNull()
            ?.takeIf { it.filePath != null }
            ?: throw IllegalStateException("Extract needs a file, but received ${ref.display()}")
        return Paths.get(location.asString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun lending(): Boolean =
        lending.lending()


    override fun awaitingRelease(): Boolean =
        lending.awaitingRelease()


    override fun onExpansionClose() {
        lending.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun captureExpansionState(): Any =
        ExtractState(lending.capture(null), archives, lending.delivered(), skippedBefore)


    override fun loadExpansionState(captured: Any?) {
        val state = captured as? ExtractState
        if (state == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        archives = state.archives
        skippedBefore = state.skippedBefore
        lending.adopt(state.adoptCursor(), null)
        lending.restoreDelivered(state.entries)
    }


    private class ExtractState(
        private var cursor: Any?,
        val archives: Long,
        val entries: Long,
        val skippedBefore: Long
    ): AutoCloseable {
        fun adoptCursor(): Any? {
            val adopted = cursor
            cursor = null
            return adopted
        }


        override fun close() {
            val closing = cursor
            cursor = null
            (closing as? AutoCloseable)?.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The output lane is always `Entry`; the input must be whole files (or entries), never rows. */
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val lane = JobLaneDescriptor(entryContract)
        if (input.contract.structural is DataType.Dynamic) {
            return JobLaneAttempt(lane, null)
        }

        val inputType = input.contract.nativeByPath[DataTypePath.root]
        val structural = input.contract.structural
        val unit = structural is DataType.Opaque && !structural.nullable &&
            inputType != null &&
            inputType.className == dataUnitClassName &&
            inputType.generics.isEmpty() &&
            !inputType.nullable
        val entry = inputType != null && inputType.className == entryClassName && !inputType.nullable
        if (!unit && !entry) {
            val description = inputType?.toSimple() ?: input.contract.structural.toString()
            return JobLaneAttempt(lane, laneRequirement + description)
        }
        return JobLaneAttempt(lane, null)
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf(
            archivesKey to archives,
            archiveKey to cursor?.archiveFileName,
            entriesKey to lending.delivered(),
            skippedKey to skippedBefore + (cursor?.skipped() ?: 0L),
            entryKey to lending.lentElementName())
}
