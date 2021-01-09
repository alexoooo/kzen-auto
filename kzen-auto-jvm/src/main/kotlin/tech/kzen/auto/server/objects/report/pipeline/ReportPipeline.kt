package tech.kzen.auto.server.objects.report.pipeline

import com.lmax.disruptor.*
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.spec.OutputSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.calc.ReportFormulas
import tech.kzen.auto.server.objects.report.pipeline.input.ReportDataInput
import tech.kzen.auto.server.objects.report.pipeline.input.ReportParser
import tech.kzen.auto.server.objects.report.pipeline.input.model.BinaryDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordBuffer
import tech.kzen.auto.server.objects.report.pipeline.output.ReportOutput
import tech.kzen.auto.server.objects.report.pipeline.summary.ReportSummary
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
class ReportPipeline(
    initialReportRunSpec: ReportRunSpec,
    runDir: Path,
    taskHandle: TaskHandle?,
    private val reportWorkPool: ReportWorkPool
):
    TaskRun, AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportPipeline::class.java)

//        private const val binaryDisruptorBufferSize = 128
        private const val binaryDisruptorBufferSize = 256
//        private const val binaryDisruptorBufferSize = 512
        private const val recordDisruptorBufferSize = 32 * 1024


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
        ): ReportPipeline {
            return ReportPipeline(reportRunSpec, runDir, null, reportWorkPool)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("ArrayInDataClass")
    data class BinaryEvent(
        val data: BinaryDataBuffer = BinaryDataBuffer.ofEmpty(),
        var records: Array<RecordBuffer> = emptyArray(),
        var recordCount: Int = 0,
        var nextRecord: Int = 0
    ) {
        companion object {
            val factory = EventFactory { BinaryEvent() }
        }
    }


    data class RecordEvent(
        var noop: Boolean = false,
        var filterAllow: Boolean = false,
        val record: RecordBuffer = RecordBuffer()
    ) {
        companion object {
            val factory = EventFactory { RecordEvent() }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
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
//        val writer = FileWriter("out-x.tsv", Charsets.UTF_8)
        val recordDisruptor = setupRecordDisruptor()
        val binaryDisruptor = setupBinaryDisruptor(recordDisruptor.ringBuffer/*, writer*/)

        recordDisruptor.start()
        binaryDisruptor.start()

        val binaryRingBuffer = binaryDisruptor.ringBuffer

        while (true) {
            val sequence = binaryRingBuffer.next()
            val binaryEvent = binaryRingBuffer.get(sequence)

            val read = dataInput.poll(binaryEvent.data)
            if (! read) {
                break
            }

            binaryRingBuffer.publish(sequence)
        }

        binaryDisruptor.shutdown()
        recordDisruptor.shutdown()
//        writer.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun setupRecordDisruptor(): Disruptor<RecordEvent> {
        val recordDisruptor = Disruptor(
            RecordEvent.factory,
            recordDisruptorBufferSize,
            DaemonThreadFactory.INSTANCE,
            ProducerType.SINGLE,
            YieldingWaitStrategy()
        )

        recordDisruptor
//            .handleEventsWith(this::handleParse)
//            .then(this::handleFormulas)
            .handleEventsWith(this::handleFormulas)
            .then(this::handleFilter)
            .then(this::handleSummaryAndOutput)
//            .handleEventsWith(this::handleSummary, this::handleOutput)

        recordDisruptor.setDefaultExceptionHandler(object : ExceptionHandler<RecordEvent?> {
            override fun handleEventException(ex: Throwable?, sequence: Long, event: RecordEvent?) {
                logger.error("Record event - {}", event?.record, ex)
            }

            override fun handleOnStartException(ex: Throwable?) {
                logger.error("Record start", ex)
            }

            override fun handleOnShutdownException(ex: Throwable?) {
                logger.error("Record shutdown", ex)
            }
        })

        return recordDisruptor
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun setupBinaryDisruptor(
        recordRingBuffer: RingBuffer<RecordEvent>/*,
        writer: FileWriter*/
    ): Disruptor<BinaryEvent> {
        val binaryDisruptor = Disruptor(
            BinaryEvent.factory,
            binaryDisruptorBufferSize,
            DaemonThreadFactory.INSTANCE,
            ProducerType.SINGLE,
            YieldingWaitStrategy()
        )

        binaryDisruptor.handleEventsWith(EventHandler { binaryEvent, _, _ ->
            handleParse(binaryEvent, recordRingBuffer/*, writer*/)
        })

        binaryDisruptor.setDefaultExceptionHandler(object : ExceptionHandler<BinaryEvent?> {
            override fun handleEventException(ex: Throwable?, sequence: Long, event: BinaryEvent?) {
                logger.error("Binary event - {}", event?.data, ex)
            }

            override fun handleOnStartException(ex: Throwable?) {
                logger.error("Binary start", ex)
            }

            override fun handleOnShutdownException(ex: Throwable?) {
                logger.error("Binary shutdown", ex)
            }
        })

        return binaryDisruptor
    }


    //-----------------------------------------------------------------------------------------------------------------
//    var first = true

    @Suppress("UNUSED_PARAMETER")
    private fun handleParse(
        binaryEvent: BinaryEvent,
        recordRingBuffer: RingBuffer<RecordEvent>/*,
        writer: FileWriter*/
    ) {
//        writer.write("^^^^^ ${binaryEvent.data.length }^^^^^^^")
//        writer.write(binaryEvent.data.contents, 0, binaryEvent.data.length)

        parser.parse(binaryEvent.data, recordRingBuffer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("UNUSED_PARAMETER")
    private fun handleFormulas(event: RecordEvent, sequence: Long, endOfBatch: Boolean) {
        if (event.noop) {
            return
        }

        formulas.evaluate(event.record.item, event.record.header)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleFilter(event: RecordEvent, sequence: Long, endOfBatch: Boolean) {
        if (event.noop) {
            return
        }

        event.filterAllow = filter.test(event.record.item, event.record.header.value)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleSummary(event: RecordEvent, sequence: Long, endOfBatch: Boolean) {
        if (endOfBatch) {
            summary.handleViewRequest()
        }

        if (! event.filterAllow || event.noop) {
            return
        }

        summary.add(event.record.item, event.record.header.value)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleOutput(
        event: RecordEvent,
        sequence: Long,
        endOfBatch: Boolean
    ) {
        if (endOfBatch) {
            // NB: must be done regardless of filterAllow to avoid lock due to starvation
            output.handlePreviewRequest(reportWorkPool)
        }

        if (! event.filterAllow || event.noop) {
            return
        }

        output.add(event.record.item, event.record.header.value)
    }


    private fun handleSummaryAndOutput(
        event: RecordEvent,
        sequence: Long,
        endOfBatch: Boolean
    ) {
        handleSummary(event, sequence, endOfBatch)
        handleOutput(event, sequence, endOfBatch)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        dataInput.close()
        summary.close()
        output.close()
    }
}