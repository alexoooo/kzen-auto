package tech.kzen.auto.server.objects.report.pipeline.progress

import com.google.common.base.Stopwatch
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.model.TaskProgress
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.server.objects.report.model.ReportRunSpec
import java.util.concurrent.TimeUnit


class ReportProgress(
    reportRunSpec: ReportRunSpec,
    private val taskHandle: TaskHandle?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportProgress::class.java)

        private const val progressBytes = 1024 * 1024

//        val noop = object: ReportProgress {
//            override fun start(location: Path, rawSize: Long) {}
//            override fun next(location: Path, rawBytes: Long, extractedBytes: Long) {}
//            override fun end(location: Path) {}
//        }


//        // https://stackoverflow.com/a/5599842
//        private val units = arrayOf("B", "kB", "MB", "GB", "TB")
//
//        private fun readableFileSize(size: Long): String {
//            if (size <= 0) {
//                return "0"
//            }
//            val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
//            val value = DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble()))
//            return value + " " + units[digitGroups]
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var nextProgress = TaskProgress.ofNotStarted(
        reportRunSpec.inputs.map { it.fileName.toString() })

    private val outerStopwatch = Stopwatch.createStarted()
    private val innerStopwatch = Stopwatch.createStarted()

    private var currentBytes = 0L
    private var previousProgressBytes = 0L

    private var currentLocation: String? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun start(locationKey: String, rawSize: Long) {
        check(currentLocation == null)
        currentLocation = locationKey

        updateProgress(
            locationKey,
            "Started processing: ${FormatUtils.readableFileSize(rawSize)}")
    }


    fun next(locationKey: String, rawBytes: Long, extractedBytes: Long) {
        check(locationKey == currentLocation)

        currentBytes += rawBytes

        val innerProgressBytes = currentBytes - previousProgressBytes
        if (innerProgressBytes > progressBytes) {
            val elapsedMillis = innerStopwatch.elapsed(TimeUnit.MILLISECONDS)
            if (elapsedMillis < 1_000) {
                return
            }

            previousProgressBytes = currentBytes

            val innerPerSecond = (1000.0 * innerProgressBytes / elapsedMillis).toLong()

            val progressMessage =
                "Processed ${FormatUtils.readableFileSize(currentBytes)}, " +
                        "at ${FormatUtils.readableFileSize(innerPerSecond)}/s"

            innerStopwatch.reset().start()

            updateProgress(
                locationKey,
                progressMessage)
        }
    }


    fun end(locationKey: String) {
        val finishedCount = currentBytes
        currentBytes = 0L
        previousProgressBytes = 0L

        val overallElapsedMillis = outerStopwatch.elapsed(TimeUnit.MILLISECONDS)
        val overallPerSecond = (1000.0 * finishedCount / overallElapsedMillis).toLong()

        val message = "Done: ${FormatUtils.readableFileSize(finishedCount)} " +
                "took $outerStopwatch " +
                "at ${FormatUtils.readableFileSize(overallPerSecond)}/s"

        innerStopwatch.reset().start()
        outerStopwatch.reset().start()

        updateProgress(locationKey, message)

        currentLocation = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun updateProgress(file: String, message: String) {
        logger.info("{} - {}", file, message)

        nextProgress = nextProgress.update(
            file, message)

        taskHandle!!.update { previous ->
            previous!!.withDetail(ExecutionValue.of(nextProgress.toCollection()))
        }
    }
}