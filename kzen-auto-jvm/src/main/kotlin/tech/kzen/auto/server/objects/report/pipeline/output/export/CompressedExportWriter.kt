package tech.kzen.auto.server.objects.report.pipeline.output.export

import com.linkedin.migz.MiGzOutputStream
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import tech.kzen.auto.common.objects.document.report.output.OutputExportInfo
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.objects.report.pipeline.ProcessorPipelineStage
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.output.export.model.ExportCompression
import tech.kzen.lib.common.model.document.DocumentName
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.absolute


class CompressedExportWriter(
    private val runDir: Path,
    private val reportName: DocumentName,
    private val outputExportSpec: OutputExportSpec
):
    ProcessorPipelineStage<ProcessorOutputEvent<*>>("export-write")
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

    private var previousGroup: DataLocationGroup? = null
    private var previousGroupStart: Instant = Instant.DISTANT_FUTURE
    private var previousGroupResolvedPattern: String = ""


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.skip) {
            return
        }

        if (previousGroup != event.group) {
            openNextGroupIfRequired(event.group)
            previousGroup = event.group
        }

        write(event.exportData)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun openNextGroupIfRequired(group: DataLocationGroup) {
        if (previousGroup == null) {
            openGroupAndTrackPrevious(group)
        }
        else {
            val resolvedPattern = outputExportSpec.resolvePath(reportName, group, previousGroupStart)
            if (resolvedPattern == previousGroupResolvedPattern) {
                return
            }

            closeGroup()
            openGroupAndTrackPrevious(group)
        }
    }


    private fun openGroupAndTrackPrevious(group: DataLocationGroup) {
        previousGroupStart = Clock.System.now()
        previousGroupResolvedPattern = outputExportSpec.resolvePath(reportName, group, previousGroupStart)

        val asPath = Paths.get(previousGroupResolvedPattern)
        val resolvedInnerFilename = outputExportSpec.resolveInnerFilename(reportName, group, previousGroupStart)
        openGroup(asPath, resolvedInnerFilename)
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
//                out =
//                    BufferedOutputStream(
//                        MiGzOutputStream(
//                            BufferedOutputStream(Files.newOutputStream(file), 128 * 1024),
//                            migzThreads,
//                            MiGzOutputStream.DEFAULT_BLOCK_SIZE),
//                        128 * 1024)

                closer = out
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun write(data: DataRecordBuffer) {
        val bytes = data.bytes
        val length = data.bytesLength

        out?.write(bytes, 0, length)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun closeGroup() {
        closer?.close()
        out = null
        closer = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun preview(
        reportRunContext: ReportRunContext,
//        outputSpec: OutputExploreSpec,
        reportWorkPool: ReportWorkPool
    ): OutputInfo {
        if (! Files.exists(runDir)) {
            return OutputInfo(
                runDir.toAbsolutePath().normalize().toString(),
                null,
                null,
                OutputStatus.Missing
            )
        }

        val status = reportWorkPool.readRunStatus(reportRunContext.toSignature())
        return OutputInfo(
            runDir.toAbsolutePath().normalize().toString(),
            null,
            OutputExportInfo("hello"),
            status)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun close(error: Boolean) {
        closeGroup()
    }
}