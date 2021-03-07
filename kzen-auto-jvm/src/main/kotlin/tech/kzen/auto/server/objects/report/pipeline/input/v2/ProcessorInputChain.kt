package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.api.PipelineTerminalStep
import tech.kzen.auto.plugin.definition.ProcessorDataDefinition
import tech.kzen.auto.plugin.model.DataBlockBuffer
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.event.v2.ListPipelineOutput
import tech.kzen.auto.server.objects.report.pipeline.event.v2.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.input.v2.ProcessorInputReader.Companion.ofLiteral
import java.nio.charset.Charset
import java.util.function.Consumer


class ProcessorInputChain<T>(
        private val inputReader: ProcessorInputReader,
        processorDataDefinition: ProcessorDataDefinition<T>,
        textCharset: Charset?,
        dataBlockSize: Int = DataBlockBuffer.defaultBytesSize
):
        AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun <T> readAll(
            textBytes: ByteArray,
            processorDataDefinition: ProcessorDataDefinition<T>,
            transform: (T) -> (T) = { it },
            charset: Charset = Charsets.UTF_8,
            dataBlockSize: Int = textBytes.size
        ): List<T> {
            val chain = ProcessorInputChain(
                ofLiteral(textBytes),
                processorDataDefinition,
                charset,
                dataBlockSize)

            val builder: MutableList<T> = mutableListOf()

            chain.forEachModel { i -> builder.add(transform(i)) }

            return builder
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val dataBlockBuffer = DataBlockBuffer.ofTextOrBinary(textCharset != null, dataBlockSize)
    private val modelOutputBuffer = ListPipelineOutput { ProcessorOutputEvent<T>() }

    private val segments = processorDataDefinition.segments.map { ProcessorSegmentInstance(it) }
    private val modelBuffers = processorDataDefinition.segments.map { ListPipelineOutput(it.modelFactory) }

    @Suppress("UNCHECKED_CAST")
    private val dataRecordBuffer = modelBuffers.first() as ListPipelineOutput<DataInputEvent>

    private val decoder = textCharset?.let { ProcessorInputDecoder(it) }
    private val dataFramer = processorDataDefinition.dataFramerFactory()
    private val feeder = ProcessorFrameFeeder(dataRecordBuffer)


    private var reachedEnd = false


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
        if (reachedEnd) {
            return false
        }

        val hasNext = inputReader.poll(dataBlockBuffer)

        decoder?.decode(dataBlockBuffer)

        dataBlockBuffer.frames.clear()
        dataFramer.frame(dataBlockBuffer)

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
        val segment = segments[index] as ProcessorSegmentInstance<Model, Any>

        for (intermediateStage in segment.intermediateStages) {
            intermediateStage.process(model)
        }

        if (index + 1 == segments.size) {
            @Suppress("UNCHECKED_CAST")
            val terminal = segment.finalStage as PipelineTerminalStep<Model, ProcessorOutputEvent<T>>

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