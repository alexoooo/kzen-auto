package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.objects.document.job.JobReadConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.objects.datasource.DataSourceHost
import tech.kzen.auto.server.objects.datasource.FileDataSource
import tech.kzen.auto.server.objects.datasource.format.WholeFileFormat
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.JobLaneSample
import tech.kzen.auto.server.objects.job.worker.SourceWorker
import tech.kzen.auto.server.objects.job.worker.content.FileContent
import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest


/**
 * Selects files, one value per file, and reads none of them (docs/plans/2026-09-24_values-metadata-and-design-time-
 * types.md, R1): the payload is the file's [FileContent], opaque; the metadata is `{name, path, size, modified}`
 * (see [FileValues]). What a file holds is read further down, by `Parse` (rows, a document), `Extract` (an archive's members) or `Write` (copied bytes) —
 * never decided by this Worker or by its neighbour.
 *
 * The selection is taken once, at the first step, and is migration state, so a resumed run keeps the files it
 * started with; an edit that changes the selection is refused while the run is open over the previous one.
 */
@Reflect
class FileSourceWorker(
    output: ChannelOutput<DataValue>,
    files: List<Map<String, String>>,
    missing: String,
    private val selfLocation: ObjectLocation,
    @Service fileListingAction: FileListingAction
):
    SourceWorker(output, selfLocation),
    DataSourceHost
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val selectionAttributes = listOf(
            "files", "missing").map(::AttributeName)

        // The editor's listing actions resolve the selection without reading any file
        private val unreadFormat = WholeFileFormat("Whole file", emptyList(), false)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val source = FileDataSource(
        files, unreadFormat, missing, fileListingAction)

    override val hostedDataSource: DataSource = source

    private var selected: List<FileDataSource.SelectedFile>? = null
    private var index = 0


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun produce(emit: Emitter, control: JobControl) {
        val selection = selected
            ?: source.select(WorkerDataContext(control)).also {
                selected = it
                control.log(selfLocation, linkedMapOf("totalCount" to it.size.toLong()))
            }
        while (index < selection.size) {
            val file = selection[index]
            index += 1
            emit.send(lift(file))
        }
    }


    private fun lift(file: FileDataSource.SelectedFile): DataValue {
        val info = file.info
        val metadata = FileValues.metadata(
            info.name,
            info.path.asString(),
            info.size,
            info.modified.toEpochMilliseconds(),
            null)
        return FileValues.lift(FileContent(DataRef.of(info)), metadata)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The file value's type, whatever the files; before Run, the lane also offers the first selected files as its
     * sample (docs/plans/2026-09-24_values-metadata-and-design-time-types.md, R5), so a `Parse` below types itself
     * from what they hold. The selection is evidence: files added, removed or changed make the validation stale.
     */
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val contract = FileValues.contract(null)
        val design = context.design
            ?: return JobLaneAttempt(JobLaneDescriptor(contract), null)

        val selection =
            try {
                design.observe(
                    "the files of ${selfLocation.objectPath.name.value}",
                    { source.select(it) },
                    ::selectionDigest)
            }
            catch (e: Exception) {
                // The run fails on the same selection by name; before Run, it only means there is nothing to offer
                return JobLaneAttempt(
                    JobLaneDescriptor(contract), null, "Files not read before Run: ${e.message}")
            }
            ?: return JobLaneAttempt(JobLaneDescriptor(contract), null, partial = true)

        val files = selection.value
        val sample = JobLaneSample(
            files.take(design.budget.maxValues).map(::lift),
            files.size)
        // The selected files' sizes, for the File card to show each one's and to weigh read progress by bytes
        val listed = files.map {
            mapOf(
                JobReadConventions.fileLocationKey to it.entry.location.asString(),
                JobReadConventions.filePathKey to it.info.path.asString(),
                JobReadConventions.fileSizeKey to it.info.size)
        }
        return JobLaneAttempt(
            JobLaneDescriptor(contract, sample = sample),
            null,
            details = mapOf(JobReadConventions.filesKey to listed))
    }


    private fun selectionDigest(files: List<FileDataSource.SelectedFile>): Digest =
        Digest.build {
            addInt(files.size)
            for (file in files) {
                addUtf8(file.info.path.asString())
                addLong(file.info.size)
                addLong(file.info.modified.toEpochMilliseconds())
            }
        }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf(
            "files" to (selected?.size?.toLong() ?: 0L),
            "emitted" to index.toLong())


    //-----------------------------------------------------------------------------------------------------------------
    /** The file selection: an edit that changes it is refused while the run is open over the previous one. */
    override fun migrationKey(graphNotation: GraphNotation, location: ObjectLocation): Any =
        migrationKeyOf(graphNotation, location, selectionAttributes)


    override fun captureMigrationState(): Any =
        FileSelectionState(selected, index)


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? FileSelectionState
            ?: return
        selected = state.selected
        index = state.index
    }


    private class FileSelectionState(
        val selected: List<FileDataSource.SelectedFile>?,
        val index: Int
    )
}
