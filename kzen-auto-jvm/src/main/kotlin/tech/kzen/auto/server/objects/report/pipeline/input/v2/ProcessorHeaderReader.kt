package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.platform.DataLocation
import tech.kzen.auto.plugin.api.HeaderExtractor
import tech.kzen.auto.plugin.api.managed.TraversableProcessorOutput
import tech.kzen.auto.plugin.definition.ProcessorDataDefinition
import tech.kzen.auto.plugin.model.DataBlockBuffer
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.server.objects.report.pipeline.event.v2.ListPipelineOutput
import tech.kzen.auto.server.objects.report.pipeline.event.v2.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FileFlatDataReader
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataReader
import java.nio.charset.Charset
import java.util.function.Consumer
import kotlin.io.path.ExperimentalPathApi


class ProcessorHeaderReader<T>(
    private val processorData: ProcessorDataDefinition<T>,
    private val headerExtractor: HeaderExtractor<T>,
    private val readerFactory: () -> FlatDataReader,
    private val textCharset: Charset?,
    private val dataBufferSize: Int = DataBlockBuffer.defaultBytesSize
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        @OptIn(ExperimentalPathApi::class)
        fun <T> ofFile(
            processorData: ProcessorDataDefinition<T>,
            headerExtractor: HeaderExtractor<T>,
            dataLocation: DataLocation,
            dataEncoding: DataEncodingSpec
        ): List<String> {
            val dataReaderFactory: () -> FlatDataReader = {
                FileFlatDataReader.of(dataLocation, dataEncoding)
            }

            val processorReader = ProcessorHeaderReader(
                processorData,
                headerExtractor,
                dataReaderFactory,
                dataEncoding.textEncoding?.getOrDefault())

            return processorReader.extract()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun extract(): List<String> {
        var dataReader: TraversableProcessorFeeder? = null

        val traversable = object : TraversableProcessorOutput<T> {
            override fun poll(visitor: Consumer<ModelOutputEvent<T>>): Boolean {
                if (dataReader == null) {
                    dataReader = TraversableProcessorFeeder(readerFactory())
                }
                return dataReader!!.poll(visitor)
            }
        }

        val columnNames = headerExtractor.extract(traversable)

        dataReader?.dataReader?.close()

        return columnNames
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inner class TraversableProcessorFeeder(
        val dataReader: FlatDataReader
    ) {
        val dataBlockBuffer = DataBlockBuffer.ofTextOrBinary(textCharset != null, dataBufferSize)
        val dataRecordBuffer = ListPipelineOutput(processorData::newInputEvent)
        val modelOutputBuffer = ListPipelineOutput { ProcessorOutputEvent<T>() }

        val inputReader = ProcessorInputReader(dataReader)
        val decoder = textCharset?.let { ProcessorInputDecoder(it) }
        val dataFramer = processorData.dataFramerFactory()
        val feeder = ProcessorFrameFeeder(dataRecordBuffer)

        val segments = processorData.segments.map { ProcessorSegmentInstance(it) }

        private var reachedEnd = false


        fun poll(visitor: Consumer<ModelOutputEvent<T>>): Boolean {
            if (reachedEnd) {
                return false
            }

            val hasNext = inputReader.poll(dataBlockBuffer)

            decoder?.decode(dataBlockBuffer)

            dataFramer.frame(dataBlockBuffer)

            feeder.feed(dataBlockBuffer)

            dataRecordBuffer.flush { dataInputEvent ->
                processSegments(dataInputEvent, visitor)
            }

            return hasNext
        }


        private fun processSegments(
                dataInputEvent: DataInputEvent,
                visitor: Consumer<ModelOutputEvent<T>>
        ) {
            if (segments.size > 1) {
                TODO()
            }

            @Suppress("UNCHECKED_CAST")
            val firstSegment = segments[0] as ProcessorSegmentInstance<DataInputEvent, ProcessorOutputEvent<T>>

            for (intermediateStage in firstSegment.intermediateStages) {
                intermediateStage.process(dataInputEvent)
            }

            firstSegment.finalStage.process(dataInputEvent, modelOutputBuffer)

            modelOutputBuffer.flush {
                visitor.accept(it)
            }
        }
    }
}