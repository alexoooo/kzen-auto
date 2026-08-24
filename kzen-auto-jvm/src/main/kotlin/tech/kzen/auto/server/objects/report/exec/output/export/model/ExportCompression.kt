package tech.kzen.auto.server.objects.report.exec.output.export.model

import com.linkedin.migz.MiGzOutputStream
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


enum class ExportCompression {
    None,
    Zip,
    GZip;

    companion object {
        // see: https://github.com/airlift/aircompressor
        @Suppress("ConstPropertyName")
        private const val migzThreads = 7

        @Suppress("ConstPropertyName")
        private const val streamBufferSize = 128 * 1024


        fun byName(compressionName: String): ExportCompression {
            return when (compressionName) {
                OutputExportSpec.compressionNoneName ->
                    None

                OutputExportSpec.compressionZipName ->
                    Zip

                OutputExportSpec.compressionGzName ->
                    GZip

                else ->
                    TODO("Compression not supported (yet): $compressionName")
            }
        }


        /**
         * Wraps a raw destination stream in the compression container the [spec] selects, returning the stream to
         * write export bytes to plus the [closer][WrappedExportOutput.closer] that finalizes the container (a Zip
         * needs its single [innerName] entry closed before the stream; GZip / None close their one stream). The
         * shared none/zip/gz seam behind Report's [tech.kzen.auto.server.objects.report.exec.output.export.CompressedExportWriter]
         * and the Job `ExportWriterWorker`, so both compress byte-identically.
         */
        fun wrap(rawOutput: OutputStream, spec: OutputExportSpec, innerName: String): WrappedExportOutput {
            return when (byName(spec.compression)) {
                None -> {
                    val buffered = BufferedOutputStream(rawOutput, streamBufferSize)
                    WrappedExportOutput(buffered, buffered)
                }

                Zip -> {
                    val zipOutput = ZipOutputStream(
                        BufferedOutputStream(rawOutput, streamBufferSize))

                    zipOutput.putNextEntry(ZipEntry(innerName))

                    WrappedExportOutput(
                        zipOutput,
                        RetryingZipCloser(zipOutput::closeEntry, zipOutput::close))
                }

                GZip -> {
                    // https://stackoverflow.com/questions/1082320/what-order-should-i-use-gzipoutputstream-and-bufferedoutputstream
                    val migz = MiGzOutputStream(
                        rawOutput,
                        migzThreads,
                        MiGzOutputStream.DEFAULT_BLOCK_SIZE)

                    WrappedExportOutput(migz, migz)
                }
            }
        }
    }
}


/**
 * A compression-wrapped export destination: the [out] stream to write export bytes to, and the [closer] that
 * finalizes and closes the compression container. For a plain / GZip stream the two are the same object; for a
 * Zip the [closer] first closes the entry, then the stream.
 */
class WrappedExportOutput(
    val out: OutputStream,
    val closer: Closeable
)


/**
 * Retry-safe ownership of Zip's two independent finalization phases. The stream close is attempted even when
 * closeEntry fails. A successful phase is never repeated, while a failed stream close remains retryable by the
 * Worker's final lifecycle cleanup. Once the stream is closed, a failed entry phase cannot be retried safely and
 * a later cleanup invocation settles as a no-op.
 */
internal class RetryingZipCloser(
    private val closeEntry: () -> Unit,
    private val closeStream: () -> Unit
): Closeable {
    private var entryClosed = false
    private var streamClosed = false


    override fun close() {
        var failure: Throwable? = null

        if (!entryClosed && !streamClosed) {
            try {
                closeEntry()
                entryClosed = true
            }
            catch (e: Throwable) {
                failure = e
            }
        }

        if (!streamClosed) {
            try {
                closeStream()
                streamClosed = true
            }
            catch (e: Throwable) {
                val previous = failure
                if (previous == null) {
                    failure = e
                }
                else {
                    previous.addSuppressed(e)
                }
            }
        }

        failure?.let { throw it }
    }
}
