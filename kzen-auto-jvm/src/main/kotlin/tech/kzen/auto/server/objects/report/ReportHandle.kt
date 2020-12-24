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
import tech.kzen.auto.server.objects.report.calc.ReportFormulas
import tech.kzen.auto.server.objects.report.input.ReportInput
import tech.kzen.auto.server.objects.report.input.model.RecordHeaderBuffer
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ReportFilter
import tech.kzen.auto.server.objects.report.pipeline.ReportOutput
import tech.kzen.auto.server.objects.report.pipeline.ReportSummary
import tech.kzen.auto.server.service.ServerContext
import java.io.InputStream
import java.nio.file.Path


/**
 * TODO: optimize the following:
 *  - BufferedIndexedSignatureStore.add(LongArray)
 *  - ReportOutput.save csv generation
 *  - BufferedIndexedTextStore.add(RecordTextFlyweight)
 *  - BufferedIndexedTextStore.add(RecordTextFlyweight)
 */
class ReportHandle(
    initialReportRunSpec: ReportRunSpec,
    runDir: Path,
    taskHandle: TaskHandle?,
    reportWorkPool: ReportWorkPool
):
    TaskRun, AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        private val logger = LoggerFactory.getLogger(ReportHandle::class.java)

        private const val disruptorBufferSize = 4 * 1024
//        private const val disruptorBufferSize = 8 * 1024


        fun passivePreview(
            reportRunSpec: ReportRunSpec,
            runDir: Path,
            outputSpec: OutputSpec,
            reportWorkPool: ReportWorkPool
        ): OutputInfo {
            return ofPassive(reportRunSpec, runDir, reportWorkPool).use {
                it.outputPreview(reportRunSpec, outputSpec)
            }
        }


        fun passiveSave(
            reportRunSpec: ReportRunSpec,
            runDir: Path,
            outputSpec: OutputSpec,
            reportWorkPool: ReportWorkPool
        ): Path? {
            return ofPassive(reportRunSpec, runDir, reportWorkPool).use {
                it.outputSave(reportRunSpec, outputSpec)
            }
        }


        fun passiveDownload(
            reportRunSpec: ReportRunSpec,
            runDir: Path,
            outputSpec: OutputSpec,
            reportWorkPool: ReportWorkPool
        ): InputStream {
            return ofPassive(reportRunSpec, runDir, reportWorkPool).use {
                it.outputDownload(reportRunSpec, outputSpec)
            }
        }


        private fun ofPassive(
            reportRunSpec: ReportRunSpec,
            runDir: Path,
            reportWorkPool: ReportWorkPool
        ): ReportHandle {
            return ReportHandle(reportRunSpec, runDir, null, reportWorkPool)
        }
    }


    private data class Event(
        var filterAllow: Boolean = false,
        val recordHeader: RecordHeaderBuffer = RecordHeaderBuffer(),
        val recordItem: RecordLineBuffer = RecordLineBuffer()
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
    private val formulas = ReportFormulas(
        initialReportRunSpec.toFormulaSignature(), ServerContext.calculatedColumnEval)
    private val summary = ReportSummary(initialReportRunSpec, runDir, taskHandle)
    private val output = ReportOutput(initialReportRunSpec, runDir, taskHandle)


    //-----------------------------------------------------------------------------------------------------------------
    fun outputPreview(
        reportRunSpec: ReportRunSpec,
        outputSpec: OutputSpec
    ): OutputInfo {
        return output.preview(reportRunSpec, outputSpec, ServerContext.reportWorkPool)
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
            .handleEventsWith(this::handleFormulas)
            .handleEventsWith(this::handleFilter)
            .handleEventsWith(this::handleSummaryAndOutput)
//            .handleEventsWith(this::handleSummary, this::handleOutput)
//            .handleEventsWith(this::handleFilter)
//            .then(this::handleSummary, this::handleOutput)

        disruptor.setDefaultExceptionHandler(object : ExceptionHandler<Event?> {
            override fun handleEventException(ex: Throwable?, sequence: Long, event: Event?) {
                println("&&^% handleEventException - $ex - ${event?.recordItem}")
                ex?.printStackTrace()
            }

            override fun handleOnStartException(ex: Throwable?) {
                println("&&^% handleOnStartException - $ex")
                ex?.printStackTrace()
            }

            override fun handleOnShutdownException(ex: Throwable?) {
                println("&&^% handleOnShutdownException - $ex")
                ex?.printStackTrace()
            }
        })

        disruptor.start()

        val ringBuffer = disruptor.ringBuffer

        while (true) {
            val sequence = ringBuffer.next()
            val event = ringBuffer.get(sequence)

            val item = event.recordItem
            val header = event.recordHeader

            val read = nextRecord(item, header)
            if (! read) {
                break
            }

            ringBuffer.publish(sequence)
        }

        disruptor.shutdown()
    }


    private fun nextRecord(
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

            return true

//            println("^^%$^^ $recordLineBuffer")

//            val pass = filter.test(recordLineBuffer, recordHeaderBuffer.value)
//            if (pass) {
//                return true
//            }
//
//            recordLineBuffer.clear()
        }
    }



    @Suppress("UNUSED_PARAMETER")
    private fun handleFilter(event: Event, sequence: Long, endOfBatch: Boolean) {
        event.filterAllow = filter.test(event.recordItem, event.recordHeader.value)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleFormulas(event: Event, sequence: Long, endOfBatch: Boolean) {
        formulas.evaluate(event.recordItem, event.recordHeader.value)
//        event.recordItem

//        if (endOfBatch) {
//            summary.handleViewRequest()
//        }
//
//        summary.add(event.recordItem, event.recordHeader.value)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleSummary(event: Event, sequence: Long, endOfBatch: Boolean) {
        if (! event.filterAllow) {
            return
        }

        if (endOfBatch) {
            summary.handleViewRequest()
        }

        summary.add(event.recordItem, event.recordHeader.value)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleOutput(
        event: Event,
        sequence: Long,
        endOfBatch: Boolean
    ) {
        if (! event.filterAllow) {
            return
        }

        if (endOfBatch) {
            output.handlePreviewRequest(ServerContext.reportWorkPool)
        }

        output.add(event.recordItem, event.recordHeader.value)
    }


    private fun handleSummaryAndOutput(
        event: Event,
        sequence: Long,
        endOfBatch: Boolean
    ) {
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