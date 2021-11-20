package tech.kzen.auto.server.objects.report.exec.output.export

import com.linkedin.migz.MiGzOutputStream
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.server.objects.report.exec.ReportPipelineStage
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import tech.kzen.auto.server.objects.report.exec.output.export.model.ExportCompression
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.absolute


// see: https://github.com/airlift/aircompressor
class CompressedExportWriter(
    private val outputExportSpec: OutputExportSpec
):
    ReportPipelineStage<ReportOutputEvent<*>>("export-write")
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        private const val migzThreads = 3
//        private const val migzThreads = 5
        private const val migzThreads = 7
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var out: OutputStream? = null
    private var closer: Closeable? = null

    private var previousExportPath: Path? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ReportOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.isSkipOrSentinel()) {
//            println("saw sentinel: ${event.hasSentinel()}")
            event.completeAndClearSentinel()
            return
        }

        if (previousExportPath != event.exportPath) {
            openNextGroup(event.exportPath, event.innerFilename)
            previousExportPath = event.exportPath
        }

        write(event.exportData)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun openNextGroup(exportPath: Path, innerFilename: String) {
        if (previousExportPath != null) {
            closeGroup()
        }
        openGroup(exportPath, innerFilename)
    }


    private fun openGroup(file: Path, fileName: String) {
        Files.createDirectories(file.absolute().parent)

        when (ExportCompression.byName(outputExportSpec.compression)) {
            ExportCompression.None -> {
                out = BufferedOutputStream(Files.newOutputStream(file), 128 * 1024)
                closer = out
            }

            ExportCompression.Zip -> {
                val zipOutput = ZipOutputStream(
                    BufferedOutputStream(Files.newOutputStream(file), 128 * 1024))

                val zipEntry = ZipEntry(fileName)
                zipOutput.putNextEntry(zipEntry)

                out = zipOutput
                closer = Closeable {
                    zipOutput.closeEntry()
                    zipOutput.close()
                }
            }

            ExportCompression.GZip -> {
                // https://stackoverflow.com/questions/1082320/what-order-should-i-use-gzipoutputstream-and-bufferedoutputstream
                out =
                    BufferedOutputStream(
                        GZIPOutputStream(
                            BufferedOutputStream(Files.newOutputStream(file), 128 * 1024),
                        128 * 1024),
                    128 * 1024)

                out = MiGzOutputStream(
                    Files.newOutputStream(file),
                    migzThreads,
                    MiGzOutputStream.DEFAULT_BLOCK_SIZE)

                closer = out
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun write(data: DataRecordBuffer) {
        val bytes = data.bytes
        val length = data.bytesLength

        out!!.write(bytes, 0, length)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun closeGroup() {
        closer?.close()
        out = null
        closer = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun close(/*error: Boolean*/) {
        closeGroup()
    }
}