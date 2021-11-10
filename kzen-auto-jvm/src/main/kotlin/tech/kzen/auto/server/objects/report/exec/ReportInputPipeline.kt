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
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import tech.kzen.auto.server.objects.report.exec.event.output.DecoratorPipelineOutput
import tech.kzen.auto.server.objects.report.exec.event.output.DisruptorPipelineOutput
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataInfo
import tech.kzen.auto.server.objects.report.exec.input.model.instance.ReportDataInstance
import tech.kzen.auto.server.objects.report.exec.input.model.instance.ReportSegmentInstance
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportFrameFeeder
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportInputDecoder
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportInputFramer
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportInputReader
import tech.kzen.auto.server.objects.report.exec.trace.ReportInputTrace
import tech.kzen.auto.server.util.DisruptorUtils
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class ReportInputPipeline<Output>(
    private val input: ReportInputReader,
    private val output: PipelineOutput<ReportOutputEvent<Output>>,
    private val reportDataInstance: ReportDataInstance<Output>,
    private val dataEncodingSpec: DataEncodingSpec,
    private val flatDataInfo: FlatDataInfo,
    private val trace: ReportInputTrace,
    private val failed: AtomicBoolean
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportInputPipeline::class.java)

//        private const val binaryDisruptorBufferSize = 128
        private const val binaryDisruptorBufferSize = 256
//        private const val binaryDisruptorBufferSize = 512

        private val binaryProducerType =
            ProducerType.SINGLE
//            ProducerType.MULTI

        private val modelProducerType =
            ProducerType.SINGLE
//            ProducerType.MULTI


        private data class BinaryDisruptor(
            val disruptor: Disruptor<DataBlockBuffer>,
            val feeder: ReportFrameFeeder
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE)

    private var binaryDisruptor: BinaryDisruptor? = null
    private var binaryRingBuffer: RingBuffer<DataBlockBuffer>? = null

    private var modelDisruptorChain: List<Disruptor<*>>? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun start() {
        check(modelDisruptorChain == null)

        val modelDisruptorChain = setupModelDisruptorChain()

        @Suppress("UNCHECKED_CAST")
        val firstModelDisruptor = modelDisruptorChain.first() as Disruptor<DataInputEvent>

        val binaryDisruptor = setupBinaryDisruptor(firstModelDisruptor.ringBuffer)

        this.binaryDisruptor = binaryDisruptor
        binaryRingBuffer = binaryDisruptor.disruptor.ringBuffer

        this.modelDisruptorChain = modelDisruptorChain

        for (disruptor in modelDisruptorChain.reversed()) {
            disruptor.start()
        }
        binaryDisruptor.disruptor.start()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun setupBinaryDisruptor(
        modelRingBuffer: RingBuffer<DataInputEvent>
    ): BinaryDisruptor {
        @Suppress("DEPRECATION")
        val binaryDisruptor = Disruptor(
            { DataBlockBuffer.ofTextOrBinary(dataEncodingSpec) },
            binaryDisruptorBufferSize,
            executor,
            binaryProducerType,
            DisruptorUtils.newWaitStrategy()
        )

        var eventHandlerGroup: EventHandlerGroup<DataBlockBuffer>? = null

        val charset = dataEncodingSpec.textEncoding?.getOrDefault()
        if (charset != null) {
            val decoder = ReportInputDecoder(charset)
            eventHandlerGroup = binaryDisruptor.handleEventsWith(decoder)
        }

        val framer = ReportInputFramer(reportDataInstance.dataFramer)
        eventHandlerGroup = DisruptorUtils.addHandlers(binaryDisruptor, eventHandlerGroup, framer)

        val output = DisruptorPipelineOutput(modelRingBuffer)
        val feeder = ReportFrameFeeder(output, trace)
        eventHandlerGroup.handleEventsWith(feeder)

        binaryDisruptor.setDefaultExceptionHandler(
            loggingExceptionHandler("Binary"))

        return BinaryDisruptor(binaryDisruptor, feeder)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun setupModelDisruptorChain(): List<Disruptor<*>> {
        val chain = mutableListOf<Disruptor<*>>()

        val resetDecorator =
            DecoratorPipelineOutput(output) {
                it.group = flatDataInfo.group
                it.header.value = flatDataInfo.headerListing
                it.row.clear()
                it.skip = false
            }

        @Suppress("UNCHECKED_CAST")
        var nextOutput = resetDecorator as PipelineOutput<Any>

        for (i in reportDataInstance.segments.size - 1 downTo 0) {
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
        val segment = reportDataInstance.segments[index] as ReportSegmentInstance<Any, T>

        @Suppress("DEPRECATION")
        val segmentDisruptor = Disruptor(
            { segment.modelFactory() },
            segment.ringBufferSize,
            executor,
            modelProducerType,
            DisruptorUtils.newWaitStrategy()
        )

        var eventHandlerGroup: EventHandlerGroup<Any>? = null

        for (intermediateStage in segment.intermediateStages) {
            val handlers: List<EventHandler<Any>> = intermediateStage.map { step ->
                object: ReportPipelineStage<Any>(step.javaClass.simpleName) {
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
            object: ReportPipelineStage<Any>(finalStage.javaClass.simpleName) {
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
    fun close(awaitEndOfData: Boolean) {
        input.close()

        check(binaryDisruptor != null)
        check(modelDisruptorChain?.size == reportDataInstance.segments.size)

        if (awaitEndOfData) {
            binaryDisruptor!!.feeder.awaitEndOfData()
        }
        binaryDisruptor!!.disruptor.shutdown()

        for (i in 0 until reportDataInstance.segments.size) {
            if (awaitEndOfData) {
                reportDataInstance.segments[i].finalStage.awaitEndOfData()
            }
            modelDisruptorChain!![i].shutdown()
        }

        binaryDisruptor = null
        binaryRingBuffer = null
        modelDisruptorChain = null

        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)
    }
}