package tech.kzen.auto.server.objects.job.worker.content

import com.linkedin.migz.MiGzOutputStream
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.auto.server.objects.job.worker.WriterFilePath
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.reflect.full.createType


/**
 * Writes each [Entry]'s bytes to one file under [directory] (design §7, §7.1; borrowed elements §3.8): an
 * ordinary [TransformWorker] whose output — a [Written] record per published file — carries no owner. Like any
 * Transform its output must be consumed (channel synthesis wires adjacent pairs only), so a Job ending in
 * `Write` still needs a sink such as `Result`. The bytes are streamed through the encoder [coding] selects
 * (`gzip` is the same MiGz encoder Report's export uses, so the bytes match
 * [tech.kzen.auto.server.objects.report.exec.output.export.model.ExportCompression]; `none` copies) into a
 * temporary in the target directory, finalized, closed, then published by atomic move. `Written` is emitted
 * only after publication; any failure removes the temporary and publishes nothing.
 *
 * [name] is field interpolation only: `${name}`, `${size}`, `${kind}`, `${parent.name}` read the entry, and
 * `${extension}` is the coding's suffix. The interpolated name is normalized and must stay inside
 * [directory]; an escaping, absolute or rooted name fails by name. [existing] governs a destination that is
 * already there: `fail` (default), `replace` (atomic replace) or `skip` (nothing written, nothing emitted).
 *
 * The entry is read inside the callback, under [JobControl.runBlockingIo]; nothing of it is kept, so its source
 * advances as soon as the callback returns.
 */
@Reflect
class WriteWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val directory: String,
    private val name: String,
    private val coding: String,
    private val existing: String,
    selfLocation: ObjectLocation,
    @Service private val fileListingAction: FileListingAction
):
    TransformWorker(input, output, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val codingGzip = "gzip"
        const val codingNone = "none"

        const val existingFail = "fail"
        const val existingReplace = "replace"
        const val existingSkip = "skip"

        const val defaultName = "\${name}\${extension}"

        private const val migzThreads = 7
        private const val streamBufferSize = 128 * 1024
        private const val copyBufferSize = 64 * 1024

        private val placeholder = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_.]*)}")

        /**
         * Test seam: wraps the encoder so a test can inject a failure at finalization (§7.1 "no published file
         * and no temporary") or hold the writer inside an entry. Null in production.
         */
        @Volatile
        internal var encoderInterceptor: ((OutputStream) -> OutputStream)? = null
    }


    private val gzip = when (coding.trim().lowercase()) {
        codingGzip -> true
        codingNone -> false
        else -> throw IllegalArgumentException("Unsupported coding '$coding'; expected $codingGzip or $codingNone")
    }

    private val onExisting = existing.trim().lowercase().ifEmpty { existingFail }.also {
        require(it == existingFail || it == existingReplace || it == existingSkip) {
            "Unsupported existing '$existing'; expected $existingFail, $existingReplace or $existingSkip"
        }
    }

    private val template = name.ifBlank { defaultName }
    private val writtenContract: DataContract = JobDataValues.describe(Written::class.createType())

    private var root: Path? = null
    private var written = 0L
    private var skipped = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStart(control: JobControl) {
        val resolved = WriterFilePath.resolve(directory)
        control.runBlockingIo { Files.createDirectories(resolved) }
        root = resolved
    }


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val entry = JobDataValues.native(element) as? Entry
            ?: throw IllegalArgumentException(
                "Write expects an Entry; received ${JobDataValues.native(element)?.javaClass?.name}")
        val target = destination(entry)

        if (Files.exists(target)) {
            when (onExisting) {
                existingSkip -> {
                    skipped += 1
                    return
                }
                existingFail -> throw IllegalStateException(
                    "Destination for entry '${entry.name}' already exists: $target")
            }
        }

        val size = control.runBlockingIo { writeAndPublish(entry, target) }
        val ref = WriterFilePath.finalizedRef(target, control, fileListingAction)
        written += 1
        emit.send(JobDataValues.lift(Written(entry.name, ref, size), writtenContract))
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("written" to written, "skipped" to skipped)


    //-----------------------------------------------------------------------------------------------------------------
    private fun writeAndPublish(entry: Entry, target: Path): Long {
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".${target.fileName}.", ".part")
        var published = false
        try {
            entry.content.open().use { source ->
                encoder(Files.newOutputStream(temporary)).use { sink ->
                    val buffer = ByteArray(copyBufferSize)
                    while (true) {
                        val count = source.read(buffer, 0, buffer.size)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                    }
                }
            }
            try {
                if (onExisting == existingReplace) {
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
            published = true
            return Files.size(target)
        }
        finally {
            if (!published) {
                Files.deleteIfExists(temporary)
            }
        }
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
    private fun destination(entry: Entry): Path {
        val base = checkNotNull(root) { "Write was not started" }
        val interpolated = placeholder.replace(template) { match -> field(entry, match.groupValues[1]) }
        val relative = containedRelative(entry.name, interpolated)
        val target = base.resolve(relative).normalize()
        check(target.startsWith(base) && target != base) {
            "Entry '${entry.name}' resolves outside the output directory: $interpolated"
        }
        return target
    }


    private fun field(entry: Entry, key: String): String =
        when (key) {
            "name" -> entry.name
            "size" -> entry.size.toString()
            "kind" -> entry.kind
            "modifiedEpochMillis" -> entry.modifiedEpochMillis?.toString() ?: ""
            "parent.name" -> entry.parent.name
            "extension" -> if (gzip) ".gz" else ""
            else -> throw IllegalArgumentException(
                "Unknown field '\${$key}' in Write name '$template'")
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
