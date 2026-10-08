package tech.kzen.auto.server.objects.job.worker.content

import com.linkedin.migz.MiGzOutputStream
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.auto.server.objects.job.worker.WriterFilePath
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassName
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.reflect.full.createType
import kotlin.time.Clock


/**
 * Writes files under [directory] (design §7, §7.1; borrowed elements §3.8; format and write §4.5): an ordinary
 * [TransformWorker] whose output — a [Written] record per published file, keeping the metadata of the value it was
 * written from — carries no owner. Like any Transform its output must be consumed (channel synthesis wires
 * adjacent pairs only), so a Job ending in `Write` still needs a sink such as `Result`. The bytes are streamed
 * through the encoder [compression] selects (`gzip` is the same MiGz encoder Report's export uses, so the bytes
 * match [tech.kzen.auto.server.objects.report.exec.output.export.model.ExportCompression]; `none` copies) into a
 * temporary in the target directory, finalized, closed, then published by atomic move. `Written` is emitted only
 * after publication; any failure or cancellation removes the temporary and publishes nothing.
 *
 * What it writes is decided by its input lane before Run: each [Content] value is one file, and a stream of
 * [Bytes] chunks is one file per maximal run of consecutive chunks with the same `name` metadata (`Format`'s
 * output). Any other lane is a validation error naming the fix; an untyped lane is decided by its first value.
 *
 * [name] interpolates the value's metadata: `${name}`, `${size}`, `${modified}`, `${parent.name}` (any dotted
 * path into it) read the file's description, or a chunk's `{name, parent}`. Two placeholders are the writer's
 * own: `${extension}` is the compression's suffix, and `${time}` is when this Write started, formatted as Report's
 * export path formats it ([FormatUtils.formatFilenameTime]) so one run's files share it. The interpolated name is
 * normalized and must stay inside [directory]; an escaping, absolute or rooted name fails by name. [existing]
 * governs a destination that is already there: `fail` (default), `replace` (atomic replace) or `skip` (nothing
 * written, nothing emitted; for chunks, the whole run of that name is dropped).
 *
 * A content is read inside the callback, under [JobControl.runBlockingIo]; nothing of it is kept, so its source
 * advances as soon as the callback returns. The copy honours the interrupt a stop delivers between reads (file
 * reads do not), so stopping inside a large entry ends the Write as cancelled without publishing the partial file.
 * Each read is reported as activity ([JobControl.reportActivity]), so a long copy is not taken for a stall. A chunk
 * is pooled and recycled once its callback returns, so it is appended to the open output inside the callback.
 *
 * Chunks name their outputs, so a name that returns after a different one would interleave or truncate a file: it
 * fails the run by name, as does a chunk with no name or an empty one.
 *
 * Over chunks, a live edit carries the open output into the edited instance, with the outputs already ended, the
 * counts and `${time}`. An edit that leaves where and how outputs are written alone ([directory], [name],
 * [compression]) continues it; [existing] was applied when the output began, and an edit to it applies from the
 * next output. An edit to any of the three discards the open output: it is not published, and starts again with
 * the chunks that follow, behind the header its first chunk had. A chunk marked as restarting its output
 * ([Bytes.restartsOutput]: its producer's edit changed how it encodes) discards the open output the same way. So
 * after an edit on either side the output is well-formed, holding every record if the edit kept it open, or else
 * those written after the edit; a published file is always a whole output, since what follows the edit would have to
 * reach the same file. Over [Content], a live edit restarts the Worker (the
 * [tech.kzen.auto.server.objects.job.worker.WorkerBase] default): each content is written within one callback, so
 * nothing is open across the edit.
 */
@Reflect
class WriteWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val directory: String,
    private val name: String,
    private val compression: String,
    private val existing: String,
    selfLocation: ObjectLocation,
    @Service private val fileListingAction: FileListingAction
):
    TransformWorker(input, output, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val compressionGzip = "gzip"
        const val compressionNone = "none"

        const val existingFail = "fail"
        const val existingReplace = "replace"
        const val existingSkip = "skip"

        // The writer's own placeholders, beside the metadata's (declared for the editor as `placeholders:`)
        const val extensionPlaceholder = "extension"
        const val timePlaceholder = "time"

        const val defaultName = "\${name}\${extension}"

        const val inputRequirement = "Write writes files or bytes; to write rows, add Format before it"

        private val contentClassName = ClassName(Content::class.qualifiedName!!)
        private val bytesClassName = ClassName(Bytes::class.qualifiedName!!)

        private const val migzThreads = 7
        private const val streamBufferSize = 128 * 1024
        private const val copyBufferSize = 64 * 1024

        /**
         * Test seam: wraps the encoder so a test can inject a failure at finalization (§7.1 "no published file
         * and no temporary") or hold the writer inside an entry. Null in production.
         */
        @Volatile
        internal var encoderInterceptor: ((OutputStream) -> OutputStream)? = null
    }


    private enum class Input {
        Content,
        Bytes
    }


    /**
     * The output a run of chunks named [name] goes to, beginning with [header]; [replace] is [existing] as it was
     * when the output began. [temporary] and [stream] are null when it is skipped.
     */
    private class OpenOutput(
        val name: String,
        val target: Path,
        val metadata: ValueMetadata?,
        val header: ByteArray,
        val replace: Boolean,
        val temporary: Path?,
        val stream: OutputStream?
    ) {
        /** Drops the partial output: closes its stream and deletes its temporary. */
        fun discard() {
            val open = stream
                ?: return
            try {
                open.close()
            }
            catch (_: IOException) {
                // The partial output is dropped; a failure finalizing it must not mask why it was dropped
            }
            finally {
                Files.deleteIfExists(checkNotNull(temporary))
            }
        }
    }


    /** An output an edit discarded, begun again (behind [header]) by the next chunk named [name]. */
    private class Restart(
        val name: String,
        val header: ByteArray
    )


    /** Where and how outputs are written: an edit to any of it discards the open output. */
    private data class Placement(
        val directory: String,
        val template: String,
        val gzip: Boolean
    )


    /**
     * What a live edit carries over chunks: the open output with the [placement] it was opened under, an output to
     * begin again, the outputs ended so far, the counts and the time. Closing it (the edit removed this Write)
     * discards the open output.
     */
    private class Carried(
        val placement: Placement,
        private var open: OpenOutput?,
        val restart: Restart?,
        val ended: Map<Path, String>,
        val written: Long,
        val skipped: Long,
        val time: String?
    ): AutoCloseable {
        fun adoptOpen(): OpenOutput? {
            val adopted = open
            open = null
            return adopted
        }

        override fun close() {
            adoptOpen()?.discard()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val gzip = when (compression.trim().lowercase()) {
        compressionGzip -> true
        compressionNone -> false
        else -> throw IllegalArgumentException(
            "Unsupported compression '$compression'; expected $compressionGzip or $compressionNone")
    }

    private val onExisting = existing.trim().lowercase().ifEmpty { existingFail }.also {
        require(it == existingFail || it == existingReplace || it == existingSkip) {
            "Unsupported existing '$existing'; expected $existingFail, $existingReplace or $existingSkip"
        }
    }

    private val template = name.ifBlank { defaultName }
    private val placement = Placement(directory, template, gzip)
    private val writtenContract: DataContract = JobDataValues.describe(Written::class.createType())

    private var root: Path? = null
    private var time: String? = null
    private var input: Input? = null
    private var written = 0L
    private var skipped = 0L

    // Chunks: the output being written, one an edit discarded, the destinations already ended in this run (by the
    // name that wrote them), and the name read off the last chunk's metadata, which a producer shares across an
    // output's chunks
    private var open: OpenOutput? = null
    private var restart: Restart? = null
    private val ended = HashMap<Path, String>()
    private var chunkMetadata: ValueMetadata? = null
    private var chunkName = ""


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStart(control: JobControl) {
        input = control.inputContract()?.let(::inputOf)
        val resolved = WriterFilePath.resolve(directory)
        control.runBlockingIo { Files.createDirectories(resolved) }
        root = resolved
        time = time ?: FormatUtils.formatFilenameTime(Clock.System.now())
    }


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val native = JobDataValues.native(element)
        val decided = input
            ?: inputOfNative(native).also { input = it }
        when (decided) {
            Input.Content -> writeContent(native as? Content ?: refuse(native), element, emit, control)
            Input.Bytes -> writeChunk(native as? Bytes ?: refuse(native), element, emit, control)
        }
    }


    override suspend fun onComplete(emit: Emitter, control: JobControl) {
        open?.let { end(it, emit, control) }
    }


    override suspend fun onClose() {
        val unfinished = open
            ?: return
        open = null
        unfinished.discard()
    }


    override fun captureMigrationState(): Any? {
        if (input != Input.Bytes) {
            return null
        }
        val carried = Carried(placement, open, restart, ended, written, skipped, time)
        open = null
        return carried
    }


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? Carried
        if (state == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        ended.putAll(state.ended)
        written = state.written
        skipped = state.skipped
        time = state.time
        restart = state.restart

        val output = state.adoptOpen()
            ?: return
        if (state.placement == placement) {
            open = output
        }
        else {
            abandon(output)
            restart = Restart(output.name, output.header)
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("written" to written, "skipped" to skipped)


    /** One [Written] per file, with the metadata of the value it was written from. */
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val lane = JobLaneDescriptor(writtenContract.withMetadata(input.contract.metadata))
        val error =
            try {
                inputOf(input.contract)
                null
            }
            catch (e: IllegalArgumentException) {
                e.message
            }
        return JobLaneAttempt(lane, error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** What a lane of [contract] carries; null when only a value can tell. */
    private fun inputOf(contract: DataContract): Input? {
        if (contract.structural is DataType.Dynamic) {
            return null
        }
        val native = contract.nativeByPath[DataTypePath.root]
        return when {
            native == null || native.nullable -> throw IllegalArgumentException(inputRequirement)
            native.className == contentClassName -> Input.Content
            native.className == bytesClassName -> Input.Bytes
            else -> throw IllegalArgumentException(inputRequirement)
        }
    }


    private fun inputOfNative(native: Any?): Input =
        when (native) {
            is Content -> Input.Content
            is Bytes -> Input.Bytes
            else -> refuse(native)
        }


    private fun refuse(native: Any?): Nothing =
        throw IllegalArgumentException("$inputRequirement; received ${native?.javaClass?.name}")


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun writeContent(content: Content, element: DataValue, emit: Emitter, control: JobControl) {
        val entryName = FileNameTemplate.metadataText(element.metadata?.value, FileValues.name) ?: content.descriptor().name
        val target = destination(element.metadata?.value, entryName)

        if (Files.exists(target)) {
            when (onExisting) {
                existingSkip -> {
                    skipped += 1
                    return
                }
                existingFail -> throw IllegalStateException(
                    "Destination for '$entryName' already exists: $target")
            }
        }

        val size = control.runBlockingIo { writeAndPublish(content, target, control) }
        emitWritten(entryName, target, size, element.metadata, emit, control)
    }


    private fun writeAndPublish(content: Content, target: Path, control: JobControl): Long {
        val temporary = createTemporary(target)
        var published = false
        try {
            content.open().use { source ->
                encoder(Files.newOutputStream(temporary)).use { sink ->
                    val buffer = ByteArray(copyBufferSize)
                    while (true) {
                        if (Thread.interrupted()) {
                            throw InterruptedException("Write of '$target' was stopped")
                        }
                        val count = source.read(buffer, 0, buffer.size)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                        // One large member can take minutes, with no element moving meanwhile
                        control.reportActivity()
                    }
                }
            }
            val size = publish(temporary, target, onExisting == existingReplace)
            published = true
            return size
        }
        finally {
            if (!published) {
                Files.deleteIfExists(temporary)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun writeChunk(bytes: Bytes, element: DataValue, emit: Emitter, control: JobControl) {
        val outputName = chunkName(element.metadata)
        var current = open
        if (bytes.restartsOutput()) {
            restart = null
            current?.let { abandoned ->
                control.runBlockingIo {
                    abandon(abandoned)
                    open = null
                }
            }
            current = null
        }
        if (current == null || current.name != outputName) {
            current?.let { end(it, emit, control) }
            current = begin(outputName, bytes, element.metadata, control)
        }
        val stream = current.stream
            ?: return
        control.runBlockingIo { bytes.writeTo(stream) }
    }


    private fun chunkName(metadata: ValueMetadata?): String {
        if (metadata != null && metadata === chunkMetadata) {
            return chunkName
        }
        val read = FileNameTemplate.metadataText(metadata?.value, FileValues.name)
            ?: throw IllegalArgumentException("Write needs a name for each chunk of bytes; found none")
        require(read.isNotEmpty()) {
            "Write cannot write bytes with an empty name; the name they were given resolved to nothing"
        }
        chunkMetadata = metadata
        chunkName = read
        return read
    }


    /**
     * Opens the output of a run of chunks named [outputName], [first] among them, made [open] before a cancel can
     * lose it. A first chunk that continues an output an edit discarded is put behind that output's header.
     */
    private suspend fun begin(
        outputName: String,
        first: Bytes,
        metadata: ValueMetadata?,
        control: JobControl
    ): OpenOutput {
        val target = destination(metadata?.value, outputName)
        ended[target]?.let { previous ->
            throw IllegalStateException(
                if (previous == outputName) {
                    "'$outputName' was already written in this run; sort by what the name is built from first"
                }
                else {
                    "'$outputName' would replace '$previous', written to the same file in this run: $target"
                })
        }

        val behind = restart
            ?.takeIf { it.name == outputName && first.headerLength() == 0 }
            ?.header
        restart = null
        val header = behind ?: first.copyHeader()
        val replace = onExisting == existingReplace

        return control.runBlockingIo {
            val exists = Files.exists(target)
            if (exists && onExisting == existingFail) {
                throw IllegalStateException("Destination for '$outputName' already exists: $target")
            }
            val opened =
                if (exists && onExisting == existingSkip) {
                    skipped += 1
                    OpenOutput(outputName, target, metadata, header, replace, null, null)
                }
                else {
                    val temporary = createTemporary(target)
                    val stream =
                        try {
                            encoder(Files.newOutputStream(temporary)).also { encoded ->
                                behind?.let(encoded::write)
                            }
                        }
                        catch (e: Throwable) {
                            Files.deleteIfExists(temporary)
                            throw e
                        }
                    OpenOutput(outputName, target, metadata, header, replace, temporary, stream)
                }
            open = opened
            opened
        }
    }


    /** Finalizes and publishes [output] (nothing for a skipped one), then emits its [Written]. */
    private suspend fun end(output: OpenOutput, emit: Emitter, control: JobControl) {
        open = null
        ended[output.target] = output.name
        val stream = output.stream
            ?: return
        val temporary = checkNotNull(output.temporary)

        val size = control.runBlockingIo {
            var published = false
            try {
                stream.close()
                val size = publish(temporary, output.target, output.replace)
                published = true
                size
            }
            finally {
                if (!published) {
                    Files.deleteIfExists(temporary)
                }
            }
        }
        emitWritten(output.name, output.target, size, output.metadata, emit, control)
    }


    /** Discards [output] for an edit; a skipped one no longer counts as skipped. */
    private fun abandon(output: OpenOutput) {
        output.discard()
        if (output.stream == null) {
            skipped -= 1
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun emitWritten(
        entryName: String,
        target: Path,
        size: Long,
        metadata: ValueMetadata?,
        emit: Emitter,
        control: JobControl
    ) {
        val ref = WriterFilePath.finalizedRef(target, control, fileListingAction)
        written += 1
        emit.send(JobDataValues.lift(Written(entryName, ref, size), writtenContract).withMetadata(metadata))
    }


    private fun createTemporary(target: Path): Path {
        Files.createDirectories(target.parent)
        return Files.createTempFile(target.parent, ".${target.fileName}.", ".part")
    }


    /** Moves the finalized [temporary] onto [target] at once, replacing it only if [replace]; returns its size. */
    private fun publish(temporary: Path, target: Path, replace: Boolean): Long {
        try {
            if (replace) {
                Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            else {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            }
        }
        catch (e: AtomicMoveNotSupportedException) {
            throw IllegalStateException(
                "Directory does not support atomic publication: ${target.parent}", e)
        }
        return Files.size(target)
    }


    private fun encoder(raw: OutputStream): OutputStream {
        val encoded =
            if (gzip) {
                MiGzOutputStream(raw, migzThreads, MiGzOutputStream.DEFAULT_BLOCK_SIZE)
            }
            else {
                BufferedOutputStream(raw, streamBufferSize)
            }
        return encoderInterceptor?.invoke(encoded) ?: encoded
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun destination(metadata: DataValue?, entryName: String): Path {
        val base = checkNotNull(root) { "Write was not started" }
        val interpolated = FileNameTemplate.placeholder.replace(template) { match -> field(metadata, match.groupValues[1]) }
        val relative = containedRelative(entryName, interpolated)
        val target = base.resolve(relative).normalize()
        check(target.startsWith(base) && target != base) {
            "Entry '$entryName' resolves outside the output directory: $interpolated"
        }
        return target
    }


    private fun field(metadata: DataValue?, key: String): String {
        when (key) {
            extensionPlaceholder -> return if (gzip) ".gz" else ""
            timePlaceholder -> return checkNotNull(time) { "Write was not started" }
        }
        return FileNameTemplate.metadataText(metadata, key)
            ?: throw IllegalArgumentException("Unknown field '\${$key}' in Write name '$template'")
    }


    /** Normalizes to forward slashes and rejects absolute, rooted, drive-lettered and dot-segment names (§7.1). */
    private fun containedRelative(entryName: String, raw: String): String {
        val normalized = raw.replace('\\', '/')
        check(normalized.isNotBlank()) { "Entry '$entryName' produced an empty file name" }
        check(!normalized.startsWith("/") && !Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
            "Entry '$entryName' has an absolute or rooted name: $raw"
        }
        val segments = normalized.split('/')
        check(segments.none { it.isEmpty() || it == "." || it == ".." }) {
            "Entry '$entryName' has a dot or empty path segment: $raw"
        }
        return normalized
    }
}
