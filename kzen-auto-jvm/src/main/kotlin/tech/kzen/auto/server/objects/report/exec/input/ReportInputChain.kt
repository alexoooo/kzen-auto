package tech.kzen.auto.server.objects.report.exec.input

import tech.kzen.auto.plugin.api.ReportInputTerminalStep
import tech.kzen.auto.plugin.definition.ReportDataDefinition
import tech.kzen.auto.plugin.helper.DataFrameFeeder
import tech.kzen.auto.plugin.helper.ListPipelineOutput
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import tech.kzen.auto.server.objects.report.exec.input.model.instance.ReportSegmentInstance
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportInputDecoder
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportInputFramer
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportInputReader
import java.nio.charset.Charset
import java.util.function.Consumer


class ReportInputChain<T>(
    private val inputReader: ReportInputReader,
    reportDataDefinition: ReportDataDefinition<T>,
    textCharset: Charset?,
    dataBlockSize: Int = DataBlockBuffer.defaultBytesSize
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun <T> readAll(
            textBytes: ByteArray,
            reportDataDefinition: ReportDataDefinition<T>,
            transform: (T) -> (T) = { it },
            charset: Charset = Charsets.UTF_8,
            dataBlockSize: Int = textBytes.size
        ): List<T> {
            val chain = ReportInputChain(
                ReportInputReader.ofLiteral(textBytes),
                reportDataDefinition,
                charset,
                dataBlockSize)

            val builder: MutableList<T> = mutableListOf()

            chain.forEachModel { i -> builder.add(transform(i)) }

            return builder
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val dataBlockBuffer = DataBlockBuffer.ofTextOrBinary(textCharset != null, dataBlockSize)
    private val modelOutputBuffer = ListPipelineOutput { ReportOutputEvent<T>() }

    private val segments = reportDataDefinition.segments.map { ReportSegmentInstance(it) }
    private val modelBuffers = reportDataDefinition.segments.map { ListPipelineOutput(it.modelFactory) }
    private val segmentIndexes = LongArray(segments.size) { 0 }

    @Suppress("UNCHECKED_CAST")
    private val dataRecordBuffer = modelBuffers.first() as ListPipelineOutput<DataInputEvent>

    private val decoder = textCharset?.let { ReportInputDecoder(it) }
    private val framer = ReportInputFramer(reportDataDefinition.dataFramerFactory())
    private val feeder = DataFrameFeeder(dataRecordBuffer)


//    private var reachedEnd = false


    //-----------------------------------------------------------------------------------------------------------------
    fun forEachModel(visitor: Consumer<T>) {
        while (true) {
            val hasNext = poll {
                visitor.accept(it.model!!)
            }

            if (! hasNext) {
                break
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun poll(visitor: Consumer<ModelOutputEvent<T>>): Boolean {
//        if (reachedEnd) {
//            return false
//        }

        val hasNext = inputReader.poll(dataBlockBuffer)

        decoder?.decode(dataBlockBuffer)

        framer.frame(dataBlockBuffer)

        feeder.feed(dataBlockBuffer)

        dataRecordBuffer.flush { dataInputEvent ->
            processSegments(0, dataInputEvent, visitor)
        }

        return hasNext
    }


    private fun <Model> processSegments(
            index: Int,
            model: Model,
            visitor: Consumer<ModelOutputEvent<T>>
    ) {
        @Suppress("UNCHECKED_CAST")
        val segment = segments[index] as ReportSegmentInstance<Model, Any>

        val segmentIndex = segmentIndexes[index]
        segmentIndexes[index]++

        for (intermediateStage in segment.intermediateStages) {
            for (step in intermediateStage) {
                step.process(model, segmentIndex)
            }
        }

        if (index + 1 == segments.size) {
            @Suppress("UNCHECKED_CAST")
            val terminal = segment.finalStage as ReportInputTerminalStep<Model, ReportOutputEvent<T>>

            terminal.process(model, modelOutputBuffer)

            modelOutputBuffer.flush {
                visitor.accept(it)
            }
        }
        else {
            @Suppress("UNCHECKED_CAST")
            val outputModelBuffer = modelBuffers[index + 1] as ListPipelineOutput<Any>

            segment.finalStage.process(model, outputModelBuffer)

            outputModelBuffer.flush { outputModel ->
                processSegments(index + 1, outputModel, visitor)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        inputReader.close()
    }
}