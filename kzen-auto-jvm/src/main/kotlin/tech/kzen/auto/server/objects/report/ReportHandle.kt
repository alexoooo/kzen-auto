package tech.kzen.auto.server.objects.report

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.ExceptionHandler
import com.lmax.disruptor.YieldingWaitStrategy
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.server.objects.report.calc.ReportFormulas
import tech.kzen.auto.server.objects.report.input.ReportDataInput
import tech.kzen.auto.server.objects.report.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.input.model.RecordHeaderBuffer
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.input.model.ReportDataBuffer
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ReportFilter
import tech.kzen.auto.server.objects.report.pipeline.ReportOutput
import tech.kzen.auto.server.objects.report.pipeline.ReportParser
import tech.kzen.auto.server.objects.report.pipeline.ReportSummary
import tech.kzen.auto.server.service.ServerContext
import java.io.InputStream
import java.nio.file.Path


/**
 * TODO: error handling
 *
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
    private val reportWorkPool: ReportWorkPool
):
    TaskRun, AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportHandle::class.java)

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


    //-----------------------------------------------------------------------------------------------------------------
    data class Event(
        var filterAllow: Boolean = false,
        val recordHeader: RecordHeaderBuffer = RecordHeaderBuffer(),
        val recordItem: RecordLineBuffer = RecordLineBuffer()
    )


    @Suppress("ArrayInDataClass")
    data class BatchEvent(
        val data: ReportDataBuffer = ReportDataBuffer.ofEmpty(),
        var events: Array<Event> = emptyArray(),
        var length: Int = 0,
        var next: Int = 0
    ) {
        companion object {
            val factory = EventFactory { BatchEvent() }
        }

        fun clearEvents() {
            for (i in 0 until length) {
                events[i].recordHeader.value = RecordHeader.empty
                events[i].recordItem.clear()
            }
            length = 0
            next = 0
        }

        fun rewind() {
            next = 0
        }

        fun current(): Event {
            return events[next - 1]
        }

        fun next(): Event? {
            if (length <= next) {
                return null
            }
            val event = events[next]
            next++
            return event
        }

        fun addNext(): Event {
            val event = add(next)
            next++
            return event
        }

        fun removeLast() {
            length--
            next--
        }

        private fun add(index: Int): Event {
            if (events.size <= index) {
                val copy = events.copyOf(index + 1)
                for (i in length .. index) {
                    copy[i] = Event()
                }
                @Suppress("UNCHECKED_CAST")
                events = copy as Array<Event>
            }
            length = index + 1
            return events[index]
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private val input = ReportInput(initialReportRunSpec, taskHandle)
    private val dataInput = ReportDataInput(initialReportRunSpec, taskHandle)

    private val parser = ReportParser(initialReportRunSpec, taskHandle)
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
        return output.preview(reportRunSpec, outputSpec, reportWorkPool)
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
            BatchEvent.factory,
            disruptorBufferSize,
            DaemonThreadFactory.INSTANCE,
            ProducerType.SINGLE,
            YieldingWaitStrategy()
        )

        disruptor
            .handleEventsWith(this::handleParse)
            .then(this::handleFormulas)
            .then(this::handleFilter)
            .then(this::handleSummaryAndOutput)
//            .then(this::handleSummary, this::handleOutput)

        disruptor.setDefaultExceptionHandler(object : ExceptionHandler<BatchEvent?> {
            override fun handleEventException(ex: Throwable?, sequence: Long, event: BatchEvent?) {
                logger.error("Event - {}", event?.current()?.recordItem, ex)
            }

            override fun handleOnStartException(ex: Throwable?) {
                logger.error("Start", ex)
            }

            override fun handleOnShutdownException(ex: Throwable?) {
                logger.error("Shutdown", ex)
            }
        })

        disruptor.start()

        val ringBuffer = disruptor.ringBuffer

        while (true) {
            val sequence = ringBuffer.next()
            val batchEvent = ringBuffer.get(sequence)

            val read = dataInput.poll(batchEvent.data)
            if (! read) {
                break
            }

            ringBuffer.publish(sequence)
        }

        disruptor.shutdown()
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleFilter(batchEvent: BatchEvent, sequence: Long, endOfBatch: Boolean) {
        batchEvent.rewind()
        var event: Event?
        while (true) {
            event = batchEvent.next()
            if (event == null) {
                break
            }

            event.filterAllow = filter.test(event.recordItem, event.recordHeader.value)
        }
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleParse(batchEvent: BatchEvent, sequence: Long, endOfBatch: Boolean) {
        parser.parse(batchEvent)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleFormulas(batchEvent: BatchEvent, sequence: Long, endOfBatch: Boolean) {
        batchEvent.rewind()
        var event: Event?
        while (true) {
            event = batchEvent.next()
            if (event == null) {
                break
            }

            formulas.evaluate(event.recordItem, event.recordHeader.value)
        }
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleSummary(batchEvent: BatchEvent, sequence: Long, endOfBatch: Boolean) {
        if (endOfBatch) {
            summary.handleViewRequest()
        }

        batchEvent.rewind()
        var event: Event?
        while (true) {
            event = batchEvent.next()
            if (event == null) {
                break
            }

            if (! event.filterAllow) {
                continue
            }
            summary.add(event.recordItem, event.recordHeader.value)
        }
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleOutput(
        batchEvent: BatchEvent,
        sequence: Long,
        endOfBatch: Boolean
    ) {
        if (endOfBatch) {
            // NB: must be done regardless of filterAllow to avoid lock due to starvation
            output.handlePreviewRequest(reportWorkPool)
        }

        batchEvent.rewind()
        var event: Event?
        while (true) {
            event = batchEvent.next()
            if (event == null) {
                break
            }

            if (! event.filterAllow) {
                continue
            }
            output.add(event.recordItem, event.recordHeader.value)
        }
    }


    private fun handleSummaryAndOutput(
        batchEvent: BatchEvent,
        sequence: Long,
        endOfBatch: Boolean
    ) {
        handleSummary(batchEvent, sequence, endOfBatch)
        handleOutput(batchEvent, sequence, endOfBatch)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
//        input.close()
        dataInput.close()
        summary.close()
        output.close()
    }
}