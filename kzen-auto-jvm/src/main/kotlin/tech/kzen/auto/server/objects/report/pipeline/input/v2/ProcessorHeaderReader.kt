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
import java.nio.charset.Charset
import java.util.function.Consumer


class ProcessorHeaderReader<T>(
    private val processorData: ProcessorDataDefinition<T>,
    private val headerExtractor: HeaderExtractor<T>,
    private val readerFactory: () -> FlatDataReader,
    private val textCharset: Charset?,
    private val dataBufferSize: Int = DataBlockBuffer.defaultBytesSize
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
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
        var chain: ProcessorInputChain<T>? = null

        val traversable = object : TraversableProcessorOutput<T> {
            override fun poll(visitor: Consumer<ModelOutputEvent<T>>): Boolean {
                if (chain == null) {
                    chain = ProcessorInputChain(
                            ProcessorInputReader(readerFactory()),
                            processorData,
                            textCharset,
                            dataBufferSize)
                }
                return chain!!.poll(visitor)
            }
        }

        val columnNames = headerExtractor.extract(traversable)

        chain?.close()

        return columnNames
    }
}