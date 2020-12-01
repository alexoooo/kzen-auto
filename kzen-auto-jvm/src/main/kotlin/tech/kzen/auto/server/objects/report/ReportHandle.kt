package tech.kzen.auto.server.objects.report

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.ExceptionHandler
import com.lmax.disruptor.YieldingWaitStrategy
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.server.objects.report.input.ReportInput
import tech.kzen.auto.server.objects.report.input.model.RecordHeaderBuffer
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ReportFilter
import tech.kzen.auto.server.objects.report.pipeline.ReportOutput
import tech.kzen.auto.server.objects.report.pipeline.ReportSummary
import java.io.InputStream
import java.nio.file.Path


/**
 * TODO: optimize the following:
 *  - BufferedIndexedSignatureStore.add(LongArray)
 *  - ReportOutput.save csv generation
 *  - BufferedIndexedTextStore.add(RecordTextFlyweight)
 *  - BufferedIndexedTextStore.add(RecordTextFlyweight)
 *  - H2DigestIndex mightContain (and addition buffer or append?)
 */
class ReportHandle(
    initialReportRunSpec: ReportRunSpec,
    runDir: Path,
    taskHandle: TaskHandle?
):
    TaskRun, AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        private val logger = LoggerFactory.getLogger(ReportHandle::class.java)

        private const val disruptorBufferSize = 4 * 1024


        fun passivePreview(reportRunSpec: ReportRunSpec, runDir: Path, outputSpec: OutputSpec): OutputInfo {
            return ofPassive(reportRunSpec, runDir).use {
                it.outputPreview(reportRunSpec, outputSpec)
            }
        }


        fun passiveSave(reportRunSpec: ReportRunSpec, runDir: Path, outputSpec: OutputSpec): Path? {
            return ofPassive(reportRunSpec, runDir).use {
                it.outputSave(reportRunSpec, outputSpec)
            }
        }


        fun passiveDownload(reportRunSpec: ReportRunSpec, runDir: Path, outputSpec: OutputSpec): InputStream {
            return ofPassive(reportRunSpec, runDir).use {
                it.outputDownload(reportRunSpec, outputSpec)
            }
        }


        private fun ofPassive(reportRunSpec: ReportRunSpec, runDir: Path): ReportHandle {
            return ReportHandle(reportRunSpec, runDir, null)
        }
    }


    private data class Event(
        var filterAllow: Boolean = false,
        var recordHeader: RecordHeaderBuffer = RecordHeaderBuffer(),
        var recordItem: RecordLineBuffer = RecordLineBuffer()
    ) {
        companion object {
            val factory = EventFactory { Event() }

//            val translator = EventTranslatorOneArg { event: Event, _: Long, item: RecordItem ->
//                event.filterAllow = false
//                event.recordItem = item
//            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val input = ReportInput(initialReportRunSpec, taskHandle)
    private val filter = ReportFilter(initialReportRunSpec)
    private val summary = ReportSummary(initialReportRunSpec, runDir, taskHandle)
    private val output = ReportOutput(initialReportRunSpec, runDir, taskHandle)


    //-----------------------------------------------------------------------------------------------------------------
    fun outputPreview(reportRunSpec: ReportRunSpec, outputSpec: OutputSpec): OutputInfo {
        return output.preview(reportRunSpec, outputSpec)
    }


    private fun outputSave(reportRunSpec: ReportRunSpec, outputSpec: OutputSpec): Path? {
        return output.save(reportRunSpec, outputSpec)
    }


    private fun outputDownload(reportRunSpec: ReportRunSpec, outputSpec: OutputSpec): InputStream {
        return output.download(reportRunSpec, outputSpec)
    }


    fun summaryView(): TableSummary {
        return summary.view()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun run() {
        val disruptor = Disruptor(
            Event.factory,
            disruptorBufferSize,
            DaemonThreadFactory.INSTANCE,
            ProducerType.SINGLE,
            YieldingWaitStrategy()
        )

        disruptor
            .handleEventsWith(this::handleSummaryAndOutput)
//            .handleEventsWith(this::handleSummary, this::handleOutput)
//            .handleEventsWith(this::handleFilter)
//            .then(this::handleSummary, this::handleOutput)

        disruptor.setDefaultExceptionHandler(object : ExceptionHandler<Event?> {
            override fun handleEventException(ex: Throwable?, sequence: Long, event: Event?) {
                println("&&^% handleEventException - $ex - ${event?.recordItem}")
            }

            override fun handleOnStartException(ex: Throwable?) {
                println("&&^% handleOnStartException - $ex")
            }

            override fun handleOnShutdownException(ex: Throwable?) {
                println("&&^% handleOnShutdownException - $ex")
            }
        })

        disruptor.start()

        val ringBuffer = disruptor.ringBuffer

        while (true) {
            val sequence = ringBuffer.next()
            val event = ringBuffer.get(sequence)

            val item = event.recordItem
            val header = event.recordHeader

            val read = nextFiltered(item, header)
            if (! read) {
                break
            }

            ringBuffer.publish(sequence)
        }

        disruptor.shutdown()
    }


    private fun nextFiltered(
        recordLineBuffer: RecordLineBuffer,
        recordHeaderBuffer: RecordHeaderBuffer
    ): Boolean {
        recordLineBuffer.clear()

        while (true) {
            val read = input.poll(recordLineBuffer, recordHeaderBuffer)
            if (! read) {
                return false
            }

            if (recordLineBuffer.isEmpty()) {
                continue
            }

//            println("^^%$^^ $recordLineBuffer")

            val pass = filter.test(recordLineBuffer, recordHeaderBuffer.value)
            if (pass) {
                return true
            }

            recordLineBuffer.clear()
        }
    }



//    @Suppress("UNUSED_PARAMETER")
//    private fun handleFilter(event: Event, sequence: Long, endOfBatch: Boolean) {
//        val record = event.recordItem!!
//        event.filterAllow = filter.test(record)
////        if (filter.test(record)) {
////            summary.add(record)
////            output.add(record)
////        }
//    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleSummary(event: Event, sequence: Long, endOfBatch: Boolean) {
//        if (! event.filterAllow) {
//            return
//        }

        if (endOfBatch) {
            summary.handleViewRequest()
        }

        summary.add(event.recordItem, event.recordHeader.value)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleOutput(event: Event, sequence: Long, endOfBatch: Boolean) {
//        if (! event.filterAllow) {
//            return
//        }

        if (endOfBatch) {
            output.handlePreviewRequest()
        }

        output.add(event.recordItem, event.recordHeader.value)
    }


    private fun handleSummaryAndOutput(event: Event, sequence: Long, endOfBatch: Boolean) {
        handleSummary(event, sequence, endOfBatch)
        handleOutput(event, sequence, endOfBatch)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        input.close()
        summary.close()
        output.close()
    }
}