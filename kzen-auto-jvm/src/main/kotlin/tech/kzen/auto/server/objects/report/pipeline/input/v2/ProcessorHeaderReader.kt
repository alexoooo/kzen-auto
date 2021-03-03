package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.platform.DataLocation
import tech.kzen.auto.plugin.api.HeaderExtractor
import tech.kzen.auto.plugin.api.managed.TraversableProcessorOutput
import tech.kzen.auto.plugin.definition.ProcessorDataDefinition
import tech.kzen.auto.plugin.model.DataBlockBuffer
import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FileFlatDataReader
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataReader
import java.util.function.Consumer
import kotlin.io.path.ExperimentalPathApi


class ProcessorHeaderReader<T>(
    private val processorData: ProcessorDataDefinition<T>,
    private val headerExtractor: HeaderExtractor<T>,
    private val readerFactory: () -> FlatDataReader,
    private val dataIsText: Boolean,
    private val dataBufferSize: Int = DataBlockBuffer.defaultBytesSize
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        @ExperimentalPathApi
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
                dataEncoding.textEncoding != null)

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
        val dataFramer = processorData.dataFramerFactory()
        val inputReader = ProcessorInputReader(dataReader)
//        private val outputFactory = processorData.outputFactory
//        private val dataBuffer = ByteArray(1024)
        val buffer = DataBlockBuffer.ofTextOrBinary(dataIsText, dataBufferSize)
        val partialInput = processorData.newInputEvent()
//        private var reachedEnd = false


        fun poll(visitor: Consumer<ModelOutputEvent<T>>): Boolean {
//            if (reachedEnd) {
//                return
//            }

//            val result = dataReader.read(dataBuffer)
//
//            if (result.isEndOfData()) {
//                reachedEnd = true
//            }

            val hasNext = inputReader.poll(buffer)

            dataFramer.frame(buffer)
            if (! hasNext) {
                dataFramer.endOfStream(buffer)
            }

            val continuePartial = partialInput.data.length() > 0

            if (continuePartial) {
                partialInput.data.addFrame(buffer, 0)
            }

            if (buffer.frames.partialLast && buffer.frames.count == 1) {
                check(hasNext)
                return true
            }
            else if (continuePartial) {
                TODO()
            }

            val firstComplete = if (continuePartial) { 1 } else { 0 }
            val lastPartialCount = if (buffer.frames.partialLast) { 1 } else { 0 }
            val lastComplete = buffer.frames.count - lastPartialCount - 1

            for (i in firstComplete .. lastComplete) {
                val offset = buffer.frames.offsets[i]
                val length = buffer.frames.lengths[i]
                TODO()
            }

            if (buffer.frames.partialLast) {
                partialInput.data.addFrame(buffer, buffer.frames.count - 1)
            }

            return hasNext
        }
    }
}