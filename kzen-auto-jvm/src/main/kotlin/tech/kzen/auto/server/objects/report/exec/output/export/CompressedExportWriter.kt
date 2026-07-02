package tech.kzen.auto.server.objects.report.exec.output.export

import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.server.objects.report.exec.ReportPipelineStage
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import tech.kzen.auto.server.objects.report.exec.output.export.model.ExportCompression
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute


class CompressedExportWriter(
    private val outputExportSpec: OutputExportSpec
):
    ReportPipelineStage<ReportOutputEvent<*>>("export-write")
{
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

        val wrapped = ExportCompression.wrap(Files.newOutputStream(file), outputExportSpec, fileName)
        out = wrapped.out
        closer = wrapped.closer
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