package tech.kzen.auto.server.objects.report.exec.event

import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderBuffer
import java.nio.file.Path
import java.util.concurrent.CountDownLatch


class ReportOutputEvent<T>:
    ModelOutputEvent<T>()
{
    //-----------------------------------------------------------------------------------------------------------------
    override val row = FlatFileRecord()
    val normalizedRow = FlatFileRecord()

    val header = RecordHeaderBuffer()

    var group = DataLocationGroup.empty
    val exportData = DataRecordBuffer()
    var exportPath: Path = Path.of(".")
    var innerFilename: String = ""


    //-----------------------------------------------------------------------------------------------------------------
    private var sentinel: CountDownLatch? = null


    fun hasSentinel(): Boolean {
        return sentinel != null
    }


    fun setSentinel(): CountDownLatch {
        check(sentinel == null)
        val localSentinel = CountDownLatch(1)
        sentinel = localSentinel
        return localSentinel
    }


    fun isSkipOrSentinel(): Boolean {
        return skip || sentinel != null
    }


    fun completeAndClearSentinel() {
        val localSentinel = sentinel
            ?: return
        localSentinel.countDown()
        sentinel = null
    }
}