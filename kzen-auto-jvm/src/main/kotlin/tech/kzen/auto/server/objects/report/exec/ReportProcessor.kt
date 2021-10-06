package tech.kzen.auto.server.objects.report.exec

import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.ExceptionHandler
import com.lmax.disruptor.RingBuffer
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.EventHandlerGroup
import com.lmax.disruptor.dsl.ProducerType
import com.lmax.disruptor.util.DaemonThreadFactory
import org.slf4j.LoggerFactory
import tech.kzen.auto.plugin.api.managed.PipelineOutput
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.server.objects.report.exec.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.exec.event.output.DecoratorPipelineOutput
import tech.kzen.auto.server.objects.report.exec.event.output.DisruptorPipelineOutput
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataInfo
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.report.exec.input.model.instance.ProcessorDataInstance
import tech.kzen.auto.server.objects.report.exec.input.model.instance.ProcessorSegmentInstance
import tech.kzen.auto.server.objects.report.exec.input.stages.ProcessorFrameFeeder
import tech.kzen.auto.server.objects.report.exec.input.stages.ProcessorInputDecoder
import tech.kzen.auto.server.objects.report.exec.input.stages.ProcessorInputFramer
import tech.kzen.auto.server.objects.report.exec.input.stages.ProcessorInputReader
import tech.kzen.auto.server.objects.report.exec.trace.ReportInputTrace
import tech.kzen.auto.server.util.DisruptorUtils
import java.util.concurrent.atomic.AtomicBoolean


class ReportProcessor<Output>(
    private val input: ProcessorInputReader,
    private val output: PipelineOutput<ProcessorOutputEvent<Output>>,
    private val processorDataInstance: ProcessorDataInstance<Output>,
    private val dataEncodingSpec: DataEncodingSpec,
    private val flatDataInfo: FlatDataInfo,
    private val trace: ReportInputTrace,
    private val failed: AtomicBoolean
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportProcessor::class.java)

//        private const val binaryDisruptorBufferSize = 128
        private const val binaryDisruptorBufferSize = 256
//        private const val binaryDisruptorBufferSize = 512
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val disruptorChain = mutableListOf<Disruptor<*>>()
    private var binaryRingBuffer: RingBuffer<DataBlockBuffer>? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun start() {
        check(disruptorChain.isEmpty())

        val modelDisruptorChain = setupModelDisruptorChain()

        @Suppress("UNCHECKED_CAST")
        val firstModelDisruptor = modelDisruptorChain.first() as Disruptor<DataInputEvent>

        val binaryDisruptor = setupBinaryDisruptor(firstModelDisruptor.ringBuffer)

        binaryRingBuffer = binaryDisruptor.ringBuffer

        disruptorChain.add(binaryDisruptor)
        disruptorChain.addAll(modelDisruptorChain)

        for (disruptor in disruptorChain.reversed()) {
            disruptor.start()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun setupBinaryDisruptor(
        recordRingBuffer: RingBuffer<DataInputEvent>
    ): Disruptor<DataBlockBuffer> {
        val binaryDisruptor = Disruptor(
            { DataBlockBuffer.ofTextOrBinary(dataEncodingSpec) },
            binaryDisruptorBufferSize,
            DaemonThreadFactory.INSTANCE,
            ProducerType.SINGLE,
            DisruptorUtils.newWaitStrategy()
        )

        var eventHandlerGroup: EventHandlerGroup<DataBlockBuffer>? = null

        val charset = dataEncodingSpec.textEncoding?.getOrDefault()
        if (charset != null) {
            val decoder = ProcessorInputDecoder(charset)
            eventHandlerGroup = binaryDisruptor.handleEventsWith(decoder)
        }

        val framer = ProcessorInputFramer(processorDataInstance.dataFramer)
        eventHandlerGroup = DisruptorUtils.addHandlers(binaryDisruptor, eventHandlerGroup, framer)

        val output = DisruptorPipelineOutput(recordRingBuffer)
//        val feeder = ProcessorFrameFeeder(output, streamProgressTracker)
        val feeder = ProcessorFrameFeeder(output, trace)
        eventHandlerGroup.handleEventsWith(feeder)

        binaryDisruptor.setDefaultExceptionHandler(
            loggingExceptionHandler("Binary"))

        return binaryDisruptor
    }
    

    //-----------------------------------------------------------------------------------------------------------------
    private fun setupModelDisruptorChain(): List<Disruptor<*>> {
        val chain = mutableListOf<Disruptor<*>>()

        val recordHeader = RecordHeader.of(flatDataInfo.headerListing)
        val resetDecorator =
            DecoratorPipelineOutput(output) {
                it.group = flatDataInfo.group
                it.header.value = recordHeader
                it.row.clear()
                it.skip = false
            }

        @Suppress("UNCHECKED_CAST")
        var nextOutput = resetDecorator as PipelineOutput<Any>

        for (i in processorDataInstance.segments.size - 1 downTo 0) {
            val segmentDisruptor = setupModelDisruptorSegment(i, nextOutput)
            chain.add(segmentDisruptor)
            nextOutput = DisruptorPipelineOutput(segmentDisruptor.ringBuffer)
        }

        chain.reverse()

        return chain
    }


    private fun <T> setupModelDisruptorSegment(
        index: Int,
        segmentOutput: PipelineOutput<T>
    ): Disruptor<Any> {
        @Suppress("UNCHECKED_CAST")
        val segment = processorDataInstance.segments[index] as ProcessorSegmentInstance<Any, T>

        val segmentDisruptor = Disruptor(
            { segment.modelFactory() },
            segment.ringBufferSize,
            DaemonThreadFactory.INSTANCE,
            ProducerType.SINGLE,
            DisruptorUtils.newWaitStrategy()
        )

        var eventHandlerGroup: EventHandlerGroup<Any>? = null

        for (intermediateStage in segment.intermediateStages) {
            val handlers: List<EventHandler<Any>> = intermediateStage.map { step ->
                object : ReportProcessorStage<Any>(step.javaClass.simpleName) {
                    override fun onEvent(event: Any, sequence: Long, endOfBatch: Boolean) {
                        step.process(event, sequence)
                    }
                }
            }

            eventHandlerGroup = DisruptorUtils.addHandlers(
                segmentDisruptor, eventHandlerGroup, *handlers.toTypedArray())
        }

        val finalStage = segment.finalStage
        DisruptorUtils.addHandlers(segmentDisruptor, eventHandlerGroup,
            object : ReportProcessorStage<Any>(finalStage.javaClass.simpleName) {
                override fun onEvent(event: Any, sequence: Long, endOfBatch: Boolean) {
                    finalStage.process(event, segmentOutput)
                }
            }
        )

        segmentDisruptor.setDefaultExceptionHandler(
            loggingExceptionHandler("Segment ${index + 1}"))

        return segmentDisruptor
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun <T> loggingExceptionHandler(name: String): ExceptionHandler<T> {
        return object : ExceptionHandler<T> {
            override fun handleEventException(ex: Throwable, sequence: Long, event: T) {
                if (failed.get()) {
                    return
                }

                logger.error("{} event - {}", name, event, ex)
                failed.set(true)
            }

            override fun handleOnStartException(ex: Throwable) {
                if (failed.get()) {
                    return
                }

                logger.error("{} start", name, ex)
                failed.set(true)
            }

            override fun handleOnShutdownException(ex: Throwable) {
                if (failed.get()) {
                    return
                }

                logger.error("{} shutdown", name, ex)
                failed.set(true)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun poll(): Boolean {
        val ringBuffer = binaryRingBuffer
            ?: throw IllegalStateException("Not started")

        val sequence = ringBuffer.next()
        val binaryEvent = ringBuffer.get(sequence)

        val hasNext = input.poll(binaryEvent)

        ringBuffer.publish(sequence)

        return hasNext
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        for (disruptor in disruptorChain) {
            disruptor.shutdown()
        }

        input.close()
        binaryRingBuffer = null
    }
}