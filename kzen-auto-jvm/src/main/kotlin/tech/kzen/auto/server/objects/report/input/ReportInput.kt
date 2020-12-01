package tech.kzen.auto.server.objects.report.input

import com.google.common.base.Stopwatch
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.model.TaskProgress
import tech.kzen.auto.server.objects.report.input.model.RecordHeaderBuffer
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.input.read.ReportStreamReader
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import tech.kzen.auto.server.objects.report.pipeline.ReportSummary
import java.nio.file.Path
import java.util.concurrent.TimeUnit


class ReportInput(
    reportRunSpec: ReportRunSpec,
    private val taskHandle: TaskHandle?
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportInput::class.java)

//        private const val progressItems = 1_000
//        private const val progressItems = 10_000
//        private const val progressItems = 25_000
//        private const val progressItems = 100_000
        private const val progressItems = 250_000
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val remainingInputs = reportRunSpec.inputs.toMutableList()

    private var nextProgress = TaskProgress.ofNotStarted(
        reportRunSpec.inputs.map { it.fileName.toString() })

    private var currentInput: Path? = null
    private var currentStream: ReportStreamReader? = null
    private var currentCount = 0L

    private val outerStopwatch = Stopwatch.createStarted()
    private val innerStopwatch = Stopwatch.createStarted()


    //-----------------------------------------------------------------------------------------------------------------
//    fun poll(consumer: (RecordItem) -> Unit): Boolean {
//        if (taskHandle!!.cancelRequested()) {
//            return false
//        }
//
//        val nextStream = nextStream()
//            ?: return false
//
//        if (! nextStream.hasNext()) {
//            endOfStream()
//            return true
//        }
//
//        val recordItem = nextStream.next()
//        consumer.invoke(recordItem)
//
//        trackProgress()
//
//        return true
//    }


    /**
     * @return true if read
     */
    fun poll(
        recordLineBuffer: RecordLineBuffer,
        recordHeaderBuffer: RecordHeaderBuffer
    ): Boolean {
        if (taskHandle!!.cancelRequested()) {
            return false
        }

        val nextStream = nextStream()
            ?: return false

        recordHeaderBuffer.value = nextStream.header()
        val hasNext = nextStream.read(recordLineBuffer)

        if (! hasNext) {
            endOfStream()
            return true
        }

        trackProgress()
        return true
    }


    private fun nextStream(): ReportStreamReader? {
        val existingStream = currentStream
        if (existingStream != null) {
            return existingStream
        }

        if (remainingInputs.isEmpty()) {
            return null
        }

        val nextInput = remainingInputs.removeFirst()
        val newStream = ReportStreamReader(nextInput)

        currentInput = nextInput
        currentStream = newStream

        updateProgress(
            nextInput.fileName.toString(),
            "Started processing")

        return newStream
    }


    private fun endOfStream() {
        currentStream!!.close()

        val finishedInput = currentInput!!
        val finishedCount = currentCount

        currentInput = null
        currentStream = null
        currentCount = 0L

        val overallPerSecond = (1000.0 * finishedCount / outerStopwatch.elapsed(TimeUnit.MILLISECONDS)).toLong()

        innerStopwatch.reset().start()
        outerStopwatch.reset().start()

        val speedMessage =
            "at ${ReportSummary.formatCount(overallPerSecond)}/s overall"

        if (taskHandle!!.cancelRequested()) {
            val message =
                "Cancelled: " + ReportSummary.formatCount(finishedCount) + " $speedMessage"
            updateProgress(finishedInput.fileName.toString(), message)
        }
        else {
            val message =
                "Finished: " + ReportSummary.formatCount(finishedCount) + " $speedMessage"
            updateProgress(finishedInput.fileName.toString(), message)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun trackProgress() {
        if (currentCount != 0L && currentCount % progressItems == 0L) {
            val progressMessage =
                "Processed ${ReportSummary.formatCount(currentCount)}, " +
                        "at ${
                            ReportSummary.formatCount(
                                (1000.0 * progressItems / innerStopwatch.elapsed(TimeUnit.MILLISECONDS)).toLong()
                            )
                        }/s"
            innerStopwatch.reset().start()

            updateProgress(
                currentInput!!.fileName.toString(),
                progressMessage)
        }

        currentCount++
    }


    private fun updateProgress(file: String, message: String) {
        logger.info("{} - {}", file, message)

        nextProgress = nextProgress.update(
            file, message)

        taskHandle!!.update { previous ->
            previous!!.withDetail(ExecutionValue.of(nextProgress.toCollection()))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        currentStream?.close()
    }
}