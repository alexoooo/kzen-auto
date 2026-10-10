package tech.kzen.auto.server.objects.job.worker.content

import com.linkedin.migz.MiGzOutputStream
import tech.kzen.auto.common.objects.document.job.JobFieldConventions
import tech.kzen.auto.common.objects.document.job.JobOutputConventions
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.objects.job.expression.JobExpressionCompiler
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.auto.server.objects.job.worker.WriterFilePath
import tech.kzen.auto.server.objects.job.worker.format.ChunkMetadata
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassName
import java.io.BufferedOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.reflect.full.createType
import kotlin.time.Clock


/**
 * Writes files under [directory] (design §7, §7.1; borrowed elements §3.8; format and write §4.5; format groups
 * §4.1, §4.4): an ordinary [TransformWorker] whose output — a [Written] record per published file, keeping the
 * metadata of the value it was written from — carries no owner. Like any Transform its output must be consumed
 * (channel synthesis wires adjacent pairs only), so a Job ending in `Write` still needs a sink such as `Result`. The
 * bytes are streamed through the encoder [compression] selects (`gzip` is the same MiGz encoder Report's export
 * uses, so the bytes match [tech.kzen.auto.server.objects.report.exec.output.export.model.ExportCompression];
 * `none` copies) into a temporary in the target directory, finalized, closed, then published by atomic move.
 * `Written` is emitted only after publication; any failure or cancellation removes the temporary and publishes
 * nothing.
 *
 * What it writes is decided by its input lane before Run: each [Content] value is one file, and a stream of [Bytes]
 * chunks (`Format`'s output) is one file per group (the chunk metadata's `group`), any number open at once, each
 * published when its producer marks its end ([Bytes.endsOutput]). Any other lane is a validation error naming the
 * fix; an untyped lane is decided by its first value.
 *
 * Where a file goes is [directory], then [folder] and [name], Kotlin string templates over the value's metadata,
 * the Job's parameters and Write's own `compression`, `extension` and `time` ([WritePathTemplate], which makes
 * every inserted value safe). A blank [name] is the default for the input: [contentDefaultName] for files,
 * [bytesDefaultName] for bytes. Two groups that would write the same file fail the run by name. [existing] governs
 * a destination that is already there: `fail` (default), `replace` (atomic replace) or `skip` (nothing written,
 * nothing emitted; for chunks, that group's whole output is dropped).
 *
 * A content is read inside the callback, under [JobControl.runBlockingIo]; nothing of it is kept, so its source
 * advances as soon as the callback returns. The copy honours the interrupt a stop delivers between reads (file
 * reads do not), so stopping inside a large entry ends the Write as cancelled without publishing the partial file.
 * Each read is reported as activity ([JobControl.reportActivity]), so a long copy is not taken for a stall. A chunk
 * is pooled and recycled once its callback returns, so it is appended to its open output inside the callback.
 *
 * Over chunks, a live edit carries the open outputs into the edited instance, with the destinations taken, the
 * counts and `time`. An edit that leaves where and how outputs are written alone ([directory], [folder], [name],
 * [compression]) continues them; [existing] was applied when an output began, and an edit to it applies from the
 * next one. An edit to any of the four discards every open output: none is published, and each starts again with
 * its group's next chunk, behind the header its first chunk had. A chunk marked as discarding its output
 * ([Bytes.discardsOutput]: its producer's edit changed how it encodes) discards that open output the same way. So
 * after an edit on either side each output is well-formed, holding every record if the edit kept it open, or else
 * those written after the edit; a published file is always a whole output. Each discard is counted against the
 * edit and reported in progress. Over [Content], a live edit restarts the Worker (the
 * [tech.kzen.auto.server.objects.job.worker.WorkerBase] default): each content is written within one callback, so
 * nothing is open across the edit.
 */
@Reflect
class WriteWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val directory: String,
    private val folder: String,
    private val name: String,
    private val compression: String,
    private val existing: String,
    selfLocation: ObjectLocation,
    @Service private val fileListingAction: FileListingAction,
    @Service private val jobExpressionCompiler: JobExpressionCompiler
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

        const val contentDefaultName = "\${name}\${extension}"
        const val bytesDefaultName = "\${group.ifEmpty { \"output\" }}\${extension}"

        const val inputRequirement = "Write writes files or bytes; to write rows, add Format before it"

        private val nameAttribute = AttributeName("name")

        private const val gzipSuffix = "gz"

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
     * The output of [group], at [target] ([path] below the directory), beginning with [header]; [replace] is
     * [existing] as it was when the output began. [temporary], [stream] and [counted] are null when it is skipped;
     * [counted] is the file side of [stream], below its encoder.
     */
    private class OpenOutput(
        val group: String,
        val path: String,
        val target: Path,
        val metadata: ValueMetadata?,
        val header: ByteArray,
        val replace: Boolean,
        val temporary: Path?,
        val stream: OutputStream?,
        val counted: CountingOutputStream?
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


    /** Counts the bytes that reach [out]; written by one thread at a time, read by [progress] from another. */
    private class CountingOutputStream(out: OutputStream): FilterOutputStream(out) {
        @Volatile
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count += 1
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }


    /** How many open outputs the live edit at [atMillis] discarded. */
    private class Discard(
        val atMillis: Long,
        var outputs: Long
    )


    /** Where and how outputs are written: an edit to any of it discards the open outputs. */
    private data class Placement(
        val directory: String,
        val folder: String,
        val name: String,
        val gzip: Boolean
    )


    /**
     * What a live edit carries over chunks: the open outputs with the [placement] they were opened under, the
     * headers of outputs to begin again, the destinations taken, the counts, the discards and the time. Closing it
     * (the edit removed this Write) discards the open outputs.
     */
    private class Carried(
        val placement: Placement,
        private var open: Map<String, OpenOutput>,
        val restarts: Map<String, ByteArray>,
        val claimed: Map<Path, String>,
        val written: Long,
        val skipped: Long,
        val publishedBytes: Long,
        val discards: List<Discard>,
        val time: String?
    ): AutoCloseable {
        fun adoptOpen(): Map<String, OpenOutput> {
            val adopted = open
            open = emptyMap()
            return adopted
        }

        override fun close() {
            adoptOpen().values.forEach(OpenOutput::discard)
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

    private val placement = Placement(directory, folder, name, gzip)
    private val writtenContract: DataContract = JobDataValues.describe(Written::class.createType())
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

    private var root: Path? = null
    private var time: String? = null
    private var input: Input? = null
    private var template: WritePathTemplate? = null
    private var compiled: WritePathTemplate.Compiled? = null
    private var compiledFor: DataContract? = null
    private var written = 0L
    private var skipped = 0L
    private var publishedBytes = 0L

    // What live edits discarded, one entry per edit; the latest edit was at editAtMillis
    private val discards = ArrayList<Discard>()
    private var editAtMillis: Long? = null

    // Chunks: the outputs being written by group, the headers of outputs an edit discarded (by group), the
    // destinations taken in this run (by the group that took them), and the group read off the last chunk's
    // metadata, which a producer shares across an output's chunks
    private val open = LinkedHashMap<String, OpenOutput>()
    private val restarts = HashMap<String, ByteArray>()
    private val claimed = HashMap<Path, String>()
    private var chunkMetadata: ValueMetadata? = null
    private var chunkGroup = ""


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStart(control: JobControl) {
        control.inputContract()?.let(::inputOf)?.let(::decide)
        val resolved = WriterFilePath.resolve(directory)
        control.runBlockingIo { Files.createDirectories(resolved) }
        root = resolved
        time = time ?: FormatUtils.formatFilenameTime(Clock.System.now())
    }


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val native = JobDataValues.native(element)
        val decided = input
            ?: inputOfNative(native).also(::decide)
        when (decided) {
            Input.Content -> writeContent(native as? Content ?: refuse(native), element, emit, control)
            Input.Bytes -> writeChunk(native as? Bytes ?: refuse(native), element, emit, control)
        }
    }


    override suspend fun onComplete(emit: Emitter, control: JobControl) {
        val unfinished = open.values.map { "'${it.path}'" } + restarts.keys.map { "group '$it'" }
        check(unfinished.isEmpty()) {
            "Input ended before the end of ${unfinished.joinToString()}; its producer marks the end of each output"
        }
    }


    override suspend fun onClose() {
        open.values.forEach(OpenOutput::discard)
        open.clear()
    }


    override fun captureMigrationState(): Any? {
        if (input != Input.Bytes) {
            return null
        }
        val carried = Carried(
            placement, LinkedHashMap(open), HashMap(restarts), HashMap(claimed),
            written, skipped, publishedBytes, discards.toList(), time)
        open.clear()
        return carried
    }


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? Carried
        if (state == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        claimed.putAll(state.claimed)
        restarts.putAll(state.restarts)
        written = state.written
        skipped = state.skipped
        publishedBytes = state.publishedBytes
        discards.addAll(state.discards)
        editAtMillis = Clock.System.now().toEpochMilliseconds()
        time = state.time

        val outputs = state.adoptOpen()
        if (state.placement == placement) {
            open.putAll(outputs)
            return
        }
        for (output in outputs.values) {
            abandon(output)
            restarts[output.group] = output.header
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> {
        var writing = 0L
        var writingPath: String? = null
        var openBytes = 0L
        for (output in open.values) {
            val counted = output.counted
                ?: continue
            writing += 1
            writingPath = output.path
            openBytes += counted.count
        }
        val progress = mutableMapOf<String, Any?>(
            JobOutputConventions.writeFilesKey to written,
            JobOutputConventions.writeSkippedKey to skipped,
            JobOutputConventions.writeBytesKey to publishedBytes + openBytes,
            JobOutputConventions.writeOpenKey to writing)
        if (writing == 1L) {
            progress[JobOutputConventions.writeOpenNameKey] = writingPath
        }
        if (discards.isNotEmpty()) {
            progress[JobOutputConventions.writeDiscardsKey] = discards.map {
                mapOf(
                    JobOutputConventions.discardAtKey to it.atMillis,
                    JobOutputConventions.discardOutputsKey to it.outputs)
            }
        }
        return progress
    }


    /** One [Written] per file, with the metadata of the value it was written from. */
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val lane = JobLaneDescriptor(writtenContract.withMetadata(input.contract.metadata))
        val decided =
            try {
                inputOf(input.contract)
            }
            catch (e: IllegalArgumentException) {
                return JobLaneAttempt(lane, e.message)
            }

        // The name a blank File name stands for, shown in the field, once the lane says what it carries
        val details = decided?.let { mapOf(JobFieldConventions.defaultKey(nameAttribute) to defaultName(it)) }
        val attempt =
            try {
                val warnings = template(decided).check(input.contract, context.parameters, context.classLoader)
                JobLaneAttempt(lane, null, warnings.joinToString("; ").ifBlank { null })
            }
            catch (e: IllegalArgumentException) {
                JobLaneAttempt(lane, e.message)
            }
        return attempt.withDetails(details.orEmpty())
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


    private fun decide(decided: Input) {
        input = decided
        template = template(decided)
    }


    /** The path templates for [input]; an undecided lane is checked with the default for bytes, as both parse. */
    private fun template(input: Input?): WritePathTemplate =
        WritePathTemplate(folder, name.ifBlank { defaultName(input) }, jobExpressionCompiler)


    private fun defaultName(input: Input?): String =
        if (input == Input.Content) contentDefaultName else bytesDefaultName


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun writeContent(content: Content, element: DataValue, emit: Emitter, control: JobControl) {
        val entryName = JobDataValues.metadataText(element.metadata?.value, FileValues.name)
            ?: content.descriptor().name
        val path = path(element, compressionSuffix(), control)
        val target = destination(path)

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
        val group = chunkGroup(element.metadata)
        if (bytes.discardsOutput()) {
            restarts.remove(group)
            open.remove(group)?.let { discarded ->
                control.runBlockingIo { abandon(discarded) }
            }
            return
        }
        val output = open[group]
            ?: begin(group, bytes, element, control)
        output.stream?.let { stream ->
            control.runBlockingIo { bytes.writeTo(stream) }
        }
        if (bytes.endsOutput()) {
            end(output, emit, control)
        }
    }


    private fun chunkGroup(metadata: ValueMetadata?): String {
        if (metadata != null && metadata === chunkMetadata) {
            return chunkGroup
        }
        val read = JobDataValues.metadataText(metadata?.value, ChunkMetadata.group)
            ?: throw IllegalArgumentException("Write needs the group of each chunk of bytes; found none")
        chunkMetadata = metadata
        chunkGroup = read
        return read
    }


    /**
     * Opens the output of [group], [first] its first chunk, made [open] before a cancel can lose it. A first chunk
     * that continues an output an edit discarded is put behind that output's header.
     */
    private suspend fun begin(group: String, first: Bytes, element: DataValue, control: JobControl): OpenOutput {
        val formatExtension = JobDataValues.metadataText(
            element.metadata?.value, ChunkMetadata.format, ChunkMetadata.extension)
        val extension = (if (formatExtension.isNullOrEmpty()) "" else ".$formatExtension") + compressionSuffix()
        val path = path(element, extension, control)
        val target = destination(path)
        claimed[target]?.let { other ->
            throw IllegalStateException("'$path' is written by group '$other' and again by group '$group'")
        }

        val behind = restarts.remove(group)
            ?.takeIf { first.headerLength() == 0 }
        val header = behind ?: first.copyHeader()
        val replace = onExisting == existingReplace

        return control.runBlockingIo {
            val exists = Files.exists(target)
            if (exists && onExisting == existingFail) {
                throw IllegalStateException("Destination for '$path' already exists: $target")
            }
            val opened =
                if (exists && onExisting == existingSkip) {
                    skipped += 1
                    OpenOutput(group, path, target, element.metadata, header, replace, null, null, null)
                }
                else {
                    val temporary = createTemporary(target)
                    val counted = CountingOutputStream(Files.newOutputStream(temporary))
                    val stream =
                        try {
                            encoder(counted).also { encoded ->
                                behind?.let(encoded::write)
                            }
                        }
                        catch (e: Throwable) {
                            Files.deleteIfExists(temporary)
                            throw e
                        }
                    OpenOutput(group, path, target, element.metadata, header, replace, temporary, stream, counted)
                }
            open[group] = opened
            claimed[target] = group
            opened
        }
    }


    /** Finalizes and publishes [output] (nothing for a skipped one), then emits its [Written]. */
    private suspend fun end(output: OpenOutput, emit: Emitter, control: JobControl) {
        open.remove(output.group)
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
        emitWritten(output.path, output.target, size, output.metadata, emit, control)
    }


    /**
     * Discards [output] for an edit, counted against that edit, and frees its destination for the output that
     * starts again; a skipped one no longer counts as skipped.
     */
    private fun abandon(output: OpenOutput) {
        output.discard()
        claimed.remove(output.target)
        if (output.stream == null) {
            skipped -= 1
            return
        }
        val atMillis = editAtMillis ?: Clock.System.now().toEpochMilliseconds()
        val latest = discards.lastOrNull()
        if (latest?.atMillis == atMillis) {
            latest.outputs += 1
        }
        else {
            discards += Discard(atMillis, 1)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun emitWritten(
        name: String,
        target: Path,
        size: Long,
        metadata: ValueMetadata?,
        emit: Emitter,
        control: JobControl
    ) {
        val ref = WriterFilePath.finalizedRef(target, control, fileListingAction)
        written += 1
        publishedBytes += size
        emit.send(JobDataValues.lift(Written(name, ref, size), writtenContract).withMetadata(metadata))
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
    private fun compressionSuffix(): String =
        if (gzip) ".$gzipSuffix" else ""


    /** The path below the directory of the output [element] starts, with the templates compiled for its contract. */
    private suspend fun path(element: DataValue, extension: String, control: JobControl): String {
        val contract = element.contract
        val current = compiled
            ?.takeIf { contract === compiledFor || contract == compiledFor }
            ?: control.runBlockingIo {
                checkNotNull(template).compile(contract, control.parameters(), classLoader)
            }.also {
                compiled = it
                compiledFor = contract
            }
        return current.path(
            element,
            control::parameter,
            if (gzip) gzipSuffix else "",
            extension,
            checkNotNull(time) { "Write was not started" })
    }


    /** The file at [path] below the directory; never outside it, which [WritePathTemplate] already ensures. */
    private fun destination(path: String): Path {
        val base = checkNotNull(root) { "Write was not started" }
        val target = base.resolve(path).normalize()
        check(target.startsWith(base) && target != base) {
            "'$path' resolves outside the output directory"
        }
        return target
    }
}
