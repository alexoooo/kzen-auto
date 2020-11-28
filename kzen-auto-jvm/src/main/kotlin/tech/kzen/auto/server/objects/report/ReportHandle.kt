package tech.kzen.auto.server.objects.report

import com.lmax.disruptor.EventFactory
import com.lmax.disruptor.EventTranslatorOneArg
import com.lmax.disruptor.YieldingWaitStrategy
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.server.objects.report.model.RecordItem
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ReportFilter
import tech.kzen.auto.server.objects.report.pipeline.ReportInput
import tech.kzen.auto.server.objects.report.pipeline.ReportOutput
import tech.kzen.auto.server.objects.report.pipeline.ReportSummary
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.Executors


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

        private const val BUFFER_SIZE = 4 * 1024


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
        var recordItem: RecordItem? = null
    ) {
        companion object {
            val factory = EventFactory { Event() }

            val translator = EventTranslatorOneArg {
                    event: Event, _: Long, item: RecordItem ->
                event.filterAllow = false
                event.recordItem = item
            }
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
            BUFFER_SIZE,
            Executors.defaultThreadFactory(),
            ProducerType.SINGLE,
            YieldingWaitStrategy()
        )

        disruptor
            .handleEventsWith(this::handleSummaryAndOutput)
//            .handleEventsWith(this::handleSummary, this::handleOutput)
//            .handleEventsWith(this::handleFilter)
//            .then(this::handleSummary, this::handleOutput)

        disruptor.start()

        val ringBuffer = disruptor.ringBuffer

        while (true) {
            val moreRemaining = input.poll { record ->
                if (filter.test(record)) {
                    ringBuffer.publishEvent(Event.translator, record)
                }
            }

            if (! moreRemaining) {
                break
            }
        }

        disruptor.shutdown()
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleFilter(event: Event, sequence: Long, endOfBatch: Boolean) {
        val record = event.recordItem!!
        event.filterAllow = filter.test(record)
//        if (filter.test(record)) {
//            summary.add(record)
//            output.add(record)
//        }
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleSummary(event: Event, sequence: Long, endOfBatch: Boolean) {
//        if (! event.filterAllow) {
//            return
//        }

        if (endOfBatch) {
            summary.handleViewRequest()
        }

        summary.add(event.recordItem!!)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleOutput(event: Event, sequence: Long, endOfBatch: Boolean) {
//        if (! event.filterAllow) {
//            return
//        }

        if (endOfBatch) {
            output.handlePreviewRequest()
        }

        output.add(event.recordItem!!)
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