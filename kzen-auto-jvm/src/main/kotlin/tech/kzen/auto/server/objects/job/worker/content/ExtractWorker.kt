package tech.kzen.auto.server.objects.job.worker.content

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
import tech.kzen.auto.server.objects.job.worker.content.tar.TarEntryContent
import tech.kzen.auto.server.objects.job.worker.content.tar.TarGzEntryCursor
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassName


/**
 * Opens each incoming file's content as a `.tar.gz` and lends its members downstream, one at a time
 * (docs/plans/2026-09-16_borrowed-elements.md §3.2, §3.5; the "Entries" transform of the 2026-09-15 content
 * analysis §7). The input is a file value — a selected file from `File`, or a member of an enclosing archive, so
 * `Extract → Extract` unpacks nested archives — and each member is the same kind of value (see [FileValues]):
 * its [Content] as the payload, and metadata `{name, path, size, modified, kind, parent}` where `name` and `path`
 * are the member's path in the archive and `parent` is the archive's own metadata.
 *
 * Each member's content is a lent element ([CursorLending]): it is valid only until the cursor advances, so the
 * next is read only once the member's last downstream hold is released; a Worker that would keep it past its
 * callback is refused by name, and a value derived from it that carries no owner (a `Written` record, a row)
 * leaves freely. Its metadata is plain data, so it stays valid after the release. Every file member is lent;
 * choosing among them is a `Filter` over their metadata downstream, which never opens a dropped member's content.
 *
 * Live edit: the open archive moves to the rebuilt instance between members (the base carries the input
 * element; the cursor is this Worker's expansion state); the cursor never rewinds and the archive is never
 * re-opened. A change of the file selection is refused upstream, by the source's own migration key.
 */
@Reflect
class ExtractWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    ExpandingTransformWorker(input, output, selfLocation), BorrowingSource
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val contentClassName = ClassName(Content::class.qualifiedName!!)

        private const val archivesKey = "archives"
        private const val archiveKey = "archive"
        private const val entriesKey = "entries"
        private const val entryKey = "entry"

        private const val laneRequirement =
            "Extract needs file content: a File, or a member of an enclosing archive; found "
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val lending = CursorLending(selfLocation)

    @Volatile
    private var cursor: TarGzEntryCursor? = null

    private var archives = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val open = opener(JobDataValues.native(element))
        val parent = element.metadata
        try {
            lending.drain(control, emit, FileValues.contentContract, open, { member ->
                memberMetadata(member as TarEntryContent, parent)
            }) { iterator ->
                cursor = iterator as TarGzEntryCursor
            }
        }
        finally {
            cursor = null
        }
        archives += 1
    }


    private fun memberMetadata(member: TarEntryContent, parent: ValueMetadata?): ValueMetadata {
        val descriptor = member.descriptor()
        return FileValues.metadata(
            descriptor.name,
            descriptor.name,
            // A tar header always records the member's size
            checkNotNull(descriptor.length) { "Archive member '${descriptor.name}' has no size" },
            descriptor.modifiedEpochMillis,
            FileValues.kindFile,
            emptyMap(),
            parent)
    }


    private fun opener(native: Any?): () -> Iterator<*> {
        return when (native) {
            is FileContent -> {
                val path = native.path
                ({ TarGzEntryCursor(path) })
            }

            is Content -> {
                val descriptor = native.descriptor()
                ({ TarGzEntryCursor(descriptor, SequentialByteContentInputStream(native.open())) })
            }

            // Reached when the upstream lane was unknown statically, so the hint the validator would have given
            // is the run-time refusal
            else -> throw IllegalStateException(
                laneRequirement + (native?.let { "${it::class.qualifiedName}: $it" } ?: "null"))
        }
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
        ExtractState(lending.capture(null), archives, lending.delivered())


    override fun loadExpansionState(captured: Any?) {
        val state = captured as? ExtractState
        if (state == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        archives = state.archives
        lending.adopt(state.adoptCursor(), null)
        lending.restoreDelivered(state.entries)
    }


    private class ExtractState(
        private var cursor: Any?,
        val archives: Long,
        val entries: Long
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
    /**
     * The output is a file value whose metadata's `parent` is the input's metadata; the input's payload must be
     * file content (a file, or a member of an enclosing archive), never rows.
     */
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val lane = JobLaneDescriptor(FileValues.contract(emptyList(), input.contract.metadata))
        if (input.contract.structural is DataType.Dynamic) {
            return JobLaneAttempt(lane, null)
        }

        val inputType = input.contract.nativeByPath[DataTypePath.root]
        val content = inputType != null && inputType.className == contentClassName && !inputType.nullable
        if (!content) {
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
            entryKey to lending.lentElementName())
}
