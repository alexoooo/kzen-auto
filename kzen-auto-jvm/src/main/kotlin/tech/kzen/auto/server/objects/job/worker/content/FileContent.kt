package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.server.data.content.SequentialByteContent
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant


/**
 * The content of a local file selected by `File`: [ContentLifetime.Reopenable], since the file outlives any
 * reader, so a value holding it may be retained, sorted or read twice; not a lent element, and nothing to
 * release. [reference] is the file's [DataRef] (path, size and modification time as listed), which `Parse`
 * reads through the file reader chain rather than as a stream.
 */
class FileContent(
    private val ref: DataRef
): Content {
    val path: Path = Paths.get(ref.id)

    private val descriptor = ContentDescriptor(
        path.fileName.toString(),
        ref.attributes[DataRef.sizeKey]?.toLongOrNull(),
        ref.attributes[DataRef.modifiedKey]?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() })


    override fun descriptor(): ContentDescriptor = descriptor

    override fun lifetime(): ContentLifetime = ContentLifetime.Reopenable

    override fun reference(): DataRef = ref


    override fun open(): SequentialByteContent =
        InputStreamContent(Files.newInputStream(path))


    override fun toString(): String = "FileContent(${ref.id})"


    private class InputStreamContent(
        private val input: InputStream
    ): SequentialByteContent {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            return input.read(buffer, offset, length)
        }

        override fun close() {
            input.close()
        }
    }
}
