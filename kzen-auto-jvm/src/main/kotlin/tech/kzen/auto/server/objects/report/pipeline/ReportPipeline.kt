package tech.kzen.auto.server.objects.report.pipeline

import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.ExceptionHandler
import com.lmax.disruptor.RingBuffer
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
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.calc.ReportFormulas
import tech.kzen.auto.server.objects.report.pipeline.event.ReportBinaryEvent
import tech.kzen.auto.server.objects.report.pipeline.event.ReportRecordEvent
import tech.kzen.auto.server.objects.report.pipeline.event.handoff.DisruptorRecordHandoff
import tech.kzen.auto.server.objects.report.pipeline.event.handoff.RecordHandoff
import tech.kzen.auto.server.objects.report.pipeline.filter.ReportFilter
import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputDecoder
import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputLexer
import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputParser
import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputReader
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FileFlatData
import tech.kzen.auto.server.objects.report.pipeline.output.ReportOutput
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgress
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
    private val taskHandle: TaskHandle?,
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
    private val dataInput = ReportInputReader(
        initialReportRunSpec.inputs.map { FileFlatData(it) },
        ReportProgress(initialReportRunSpec, taskHandle))

    private val decoder = ReportInputDecoder()
//    private val parser = ReportParserFeeder()
    private val lexer = ReportInputLexer()
    private val lexerParser = ReportInputParser()


    private val filter = ReportFilter(initialReportRunSpec)
    private val formulas = ReportFormulas(
        initialReportRunSpec.toFormulaSignature(), ServerContext.calculatedColumnEval)
    private val summary = ReportSummary(initialReportRunSpec, runDir, taskHandle)
    private val output = ReportOutput(initialReportRunSpec, runDir, taskHandle)

    // NB: debug
//    private var writer: FileWriter? = null
//    private var first = true


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
//        writer = FileWriter("out-x.tsv", Charsets.UTF_8)

        val recordDisruptor = setupRecordDisruptor()
        val binaryDisruptor = setupBinaryDisruptor(recordDisruptor.ringBuffer)

        recordDisruptor.start()
        binaryDisruptor.start()

        val binaryRingBuffer = binaryDisruptor.ringBuffer

        while (! taskHandle!!.cancelRequested()) {
            val sequence = binaryRingBuffer.next()
            val binaryEvent = binaryRingBuffer.get(sequence)

            val read = dataInput.poll(binaryEvent.data)
            if (! read) {
                binaryEvent.noop = true
                binaryRingBuffer.publish(sequence)
                break
            }

            binaryRingBuffer.publish(sequence)
        }

        binaryDisruptor.shutdown()
        recordDisruptor.shutdown()
//        writer?.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun setupRecordDisruptor(): Disruptor<ReportRecordEvent> {
        val recordDisruptor = Disruptor(
            ReportRecordEvent.factory,
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
//            .then(this::handleSummaryAndOutput)
            .handleEventsWith(this::handleSummary, this::handleOutput)

        recordDisruptor.setDefaultExceptionHandler(object : ExceptionHandler<ReportRecordEvent?> {
            override fun handleEventException(ex: Throwable?, sequence: Long, event: ReportRecordEvent?) {
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
        recordRingBuffer: RingBuffer<ReportRecordEvent>
    ): Disruptor<ReportBinaryEvent> {
        val binaryDisruptor = Disruptor(
            ReportBinaryEvent.factory,
            binaryDisruptorBufferSize,
            DaemonThreadFactory.INSTANCE,
            ProducerType.SINGLE,
            YieldingWaitStrategy()
        )

        val disruptorRecordHandoff = DisruptorRecordHandoff(recordRingBuffer)

        binaryDisruptor
            .handleEventsWith(this::handleDecode)
            .handleEventsWith(this::handleLex)
            .handleEventsWith(EventHandler { binaryEvent, _, _ ->
                handleLexParse(binaryEvent, disruptorRecordHandoff)
            })

//        binaryDisruptor
//            .handleEventsWith(EventHandler { binaryEvent, _, _ ->
//                handleParse(binaryEvent, recordRingBuffer)
//            })

        binaryDisruptor.setDefaultExceptionHandler(object : ExceptionHandler<ReportBinaryEvent?> {
            override fun handleEventException(ex: Throwable?, sequence: Long, event: ReportBinaryEvent?) {
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
    @Suppress("UNUSED_PARAMETER")
    private fun handleDecode(
        event: ReportBinaryEvent, sequence: Long, endOfBatch: Boolean
    ) {
        if (event.noop) {
            return
        }

        decoder.decode(event.data)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleLex(
        event: ReportBinaryEvent, sequence: Long, endOfBatch: Boolean
    ) {
        if (event.noop) {
            return
        }

        lexer.tokenize(event.data)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleLexParse(
        event: ReportBinaryEvent,
        recordHandoff: RecordHandoff
    ) {
        if (event.noop) {
            return
        }

//        writer!!.write("^^^^^^^^^")

        lexerParser.parse(event.data, recordHandoff)
    }



//    @Suppress("UNUSED_PARAMETER")
//    private fun handleParse(
//        binaryEvent: BinaryEvent,
//        recordRingBuffer: RingBuffer<RecordEvent>
//    ) {
//        if (binaryEvent.noop) {
//            return
//        }
////        writer.write("^^^^^ ${binaryEvent.data.length }^^^^^^^")
////        writer.write(binaryEvent.data.contents, 0, binaryEvent.data.length)
//
//        parser.parse(binaryEvent.data, recordRingBuffer)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("UNUSED_PARAMETER")
    private fun handleFormulas(event: ReportRecordEvent, sequence: Long, endOfBatch: Boolean) {
//        if (event.noop) {
//            return
//        }

//        if (first) {
//            first = false
//            writer!!.write(RecordItemBuffer.of(event.record.header.value.headerNames).toTsv())
//            writer!!.write("\n")
//        }
//        writer!!.write(event.record.item.toTsv())
//        writer!!.write("\n")

        formulas.evaluate(event.record.item, event.record.header)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleFilter(event: ReportRecordEvent, sequence: Long, endOfBatch: Boolean) {
//        if (event.noop) {
//            return
//        }

        event.filterAllow = filter.test(event.record.item, event.record.header.value)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleSummary(event: ReportRecordEvent, sequence: Long, endOfBatch: Boolean) {
        if (endOfBatch) {
            summary.handleViewRequest()
        }

        if (! event.filterAllow /*|| event.noop*/) {
            return
        }

        summary.add(event.record.item, event.record.header.value)
    }


    @Suppress("UNUSED_PARAMETER")
    private fun handleOutput(
        event: ReportRecordEvent,
        sequence: Long,
        endOfBatch: Boolean
    ) {
        if (endOfBatch) {
            // NB: must be done regardless of filterAllow to avoid lock due to starvation
            output.handlePreviewRequest(reportWorkPool)
        }

        if (! event.filterAllow /*|| event.noop*/) {
            return
        }

        output.add(event.record.item, event.record.header.value)
    }


    private fun handleSummaryAndOutput(
        event: ReportRecordEvent,
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