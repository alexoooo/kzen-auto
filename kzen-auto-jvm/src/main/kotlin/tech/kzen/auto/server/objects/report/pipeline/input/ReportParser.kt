package tech.kzen.auto.server.objects.report.pipeline.input

import com.google.common.base.Stopwatch
import com.lmax.disruptor.RingBuffer
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.model.TaskProgress
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ReportPipeline
import tech.kzen.auto.server.objects.report.pipeline.input.model.BinaryDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordItemParser
import tech.kzen.auto.server.objects.report.pipeline.summary.ReportSummary
import java.nio.file.Path
import java.util.concurrent.TimeUnit


// TODO: factor out progress tracking
class ReportParser(
    reportRunSpec: ReportRunSpec,
    private val taskHandle: TaskHandle?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportParser::class.java)
        private const val progressItems = 1_000
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var location: Path? = null
    private var parser: RecordItemParser? = null
    private var previousRecordHeader: RecordHeader = RecordHeader.empty
    private val leftoverRecordLineBuffer = RecordItemBuffer()

    private var nextProgress = TaskProgress.ofNotStarted(
        reportRunSpec.inputs.map { it.fileName.toString() })
    private var currentCount = 0L
    private val outerStopwatch = Stopwatch.createStarted()
    private val innerStopwatch = Stopwatch.createStarted()
    private var previousInnerCount = 0L


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(data: BinaryDataBuffer, recordRingBuffer: RingBuffer<ReportPipeline.RecordEvent>) {
        if (location == null) {
            location = data.location!!
            parser = RecordItemParser.forExtension(data.innerExtension!!)
            trackProgressStart()
        }

        var offset = 0
        if (previousRecordHeader.isEmpty()) {
            val recordLength = parser!!.parseNext(
                leftoverRecordLineBuffer, data.contents, offset, data.length)

            if (recordLength != -1) {
                previousRecordHeader = RecordHeader.ofLine(leftoverRecordLineBuffer)
                leftoverRecordLineBuffer.clear()
                offset += recordLength
            }
            else {
                offset = -1
            }
        }
        else if (! leftoverRecordLineBuffer.isEmpty()) {
            val recordLength = parser!!.parseNext(
                leftoverRecordLineBuffer, data.contents, offset, data.length)

            if (recordLength != -1) {
                val sequence = recordRingBuffer.next()
                val event = recordRingBuffer.get(sequence)
                val record = event.record
                event.noop = false
                record.header.value = previousRecordHeader
                record.item.copy(leftoverRecordLineBuffer)
                recordRingBuffer.publish(sequence)

                leftoverRecordLineBuffer.clear()
                offset += recordLength
                trackProgressNext()
            }
            else {
                offset = -1
            }
        }

        if (offset != -1) {
            while (true) {
                val sequence = recordRingBuffer.next()
                val event = recordRingBuffer.get(sequence)
                val record = event.record
                record.clear()

                val recordLength = parser!!.parseNext(
                    record.item, data.contents, offset, data.length)

                if (recordLength != -1) {
                    event.noop = false
                    record.header.value = previousRecordHeader
                    recordRingBuffer.publish(sequence)

                    offset += recordLength
                    trackProgressNext()
                }
                else {
                    leftoverRecordLineBuffer.copy(record.item)

                    event.noop = true
                    recordRingBuffer.publish(sequence)
                    break
                }
            }
        }

        if (data.endOfStream) {
            if (! leftoverRecordLineBuffer.isEmpty()) {
                parser!!.endOfStream(leftoverRecordLineBuffer)

                val sequence = recordRingBuffer.next()
                val event = recordRingBuffer.get(sequence)
                val record = event.record
                record.clear()
                event.noop = false
                record.header.value = previousRecordHeader
                record.item.copy(leftoverRecordLineBuffer)
                recordRingBuffer.publish(sequence)

                leftoverRecordLineBuffer.clear()
                trackProgressNext()
            }
            previousRecordHeader = RecordHeader.empty

            trackProgressEnd(location!!)
            location = null
            parser = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun trackProgressStart() {
        updateProgress(
            location!!.fileName.toString(),
            "Started processing")
    }


    private fun trackProgressEnd(location: Path) {
        val finishedInput = location
        val finishedCount = currentCount
        currentCount = 0L
        previousInnerCount = 0L

        val overallElapsedMillis = outerStopwatch.elapsed(TimeUnit.MILLISECONDS)
        val overallPerSecond = (1000.0 * finishedCount / overallElapsedMillis).toLong()

        val speedMessage = "took $outerStopwatch " +
                "at ${ReportSummary.formatCount(overallPerSecond)}/s"

        innerStopwatch.reset().start()
        outerStopwatch.reset().start()

        val message =
            "Done: " + ReportSummary.formatCount(finishedCount) + " $speedMessage"
        updateProgress(finishedInput.fileName.toString(), message)
    }


    private fun trackProgressNext() {
        currentCount++

        if (currentCount % progressItems == 0L) {
            val elapsedMillis =  innerStopwatch.elapsed(TimeUnit.MILLISECONDS)
            if (elapsedMillis < 1_000) {
                return
            }

            val innerProgressItems = currentCount - previousInnerCount
            val innerPerSecond = (1000.0 * innerProgressItems / elapsedMillis).toLong()

            val progressMessage =
                "Processed ${ReportSummary.formatCount(currentCount)}, " +
                        "at ${ReportSummary.formatCount(innerPerSecond)}/s"

            previousInnerCount = currentCount

            innerStopwatch.reset().start()

            updateProgress(
                location!!.fileName.toString(),
                progressMessage)
        }
    }


    private fun updateProgress(file: String, message: String) {
        logger.info("{} - {}", file, message)

        nextProgress = nextProgress.update(
            file, message)

        taskHandle!!.update { previous ->
            previous!!.withDetail(ExecutionValue.of(nextProgress.toCollection()))
        }
    }
}