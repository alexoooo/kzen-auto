package tech.kzen.auto.server.objects.report.pipeline.progress

import com.google.common.base.Stopwatch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.progress.ReportFileProgress
import tech.kzen.auto.common.objects.document.report.progress.ReportProgress
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime


class ReportProgressTracker(
    dataLocations: List<DataLocation>,
    private val taskHandle: TaskHandle?,
    private val clock: Clock = Clock.System
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ReportProgressTracker::class.java)

        private const val progressBytesWatermark = 1024L * 1024
    }


    //-----------------------------------------------------------------------------------------------------------------
    inner class Buffer(
        private val dataLocation: DataLocation
    ) {
        private val innerStopwatch = Stopwatch.createUnstarted()

        @Volatile var startTime = Instant.DISTANT_PAST
        @Volatile var endTime = Instant.DISTANT_PAST

        @Volatile var running = false
        @Volatile var finished = false
        @Volatile var records: Long = 0
        @Volatile var readBytes: Long = 0
        @Volatile var uncompressedBytes: Long = 0
        @Volatile var recentBytesPerSecond: Long = 0
        @Volatile var recentRecordsPerSecond: Long = 0

        @Volatile var totalSize: Long = 0
        @Volatile var progressBytesRemaining: Long = 0
        @Volatile var previousUncompressedBytes: Long = 0
        @Volatile var previousRecords: Long = 0


        fun startReading() {
            check(! running)

            running = true
            startTime = clock.now()
            innerStopwatch.start()

            publishAndLogStarted(dataLocation, totalSize)
        }


        fun finishParsing() {
            check(running)
            check(! finished)

            running = false
            finished = true
            innerStopwatch.reset()
            endTime = clock.now()

            publishAndLogFinished(dataLocation, totalSize)
        }


        fun nextRead(nextReadBytes: Long, nextUncompressedBytes: Long) {
            readBytes += nextReadBytes
            uncompressedBytes += nextUncompressedBytes

            progressBytesRemaining -= nextUncompressedBytes
            if (progressBytesRemaining <= 0) {
                progressBytesRemaining = progressBytesWatermark

                val recentElapsedMillis = innerStopwatch.elapsed(TimeUnit.MILLISECONDS)
                if (recentElapsedMillis < 1_000) {
                    return
                }

                val recentUncompressedBytes = uncompressedBytes - previousUncompressedBytes
                previousUncompressedBytes = uncompressedBytes
                recentBytesPerSecond = (1000.0 * recentUncompressedBytes / recentElapsedMillis).toLong()

                val currentRecords = records
                val recentRecords = currentRecords - previousRecords
                previousRecords = currentRecords
                recentRecordsPerSecond = (1000.0 * recentRecords / recentElapsedMillis).toLong()

                innerStopwatch.reset().start()

                publishAndLogProcessed(dataLocation, totalSize)
            }
        }


        fun nextRecords(additionalRecords: Int) {
            records += additionalRecords
        }


        fun snapshot(): ReportFileProgress {
            val durationMillis = durationMillis()
            return ReportFileProgress(
                running,
                finished,
                durationMillis,
                records,
                readBytes,
                uncompressedBytes,
                recentBytesPerSecond,
                recentRecordsPerSecond)
        }


        @OptIn(ExperimentalTime::class)
        private fun durationMillis(): Long {
            return when {
                startTime == Instant.DISTANT_PAST ->
                    0

                endTime == Instant.DISTANT_PAST ->
                    (clock.now() - startTime).inWholeMilliseconds

                else ->
                    (endTime - startTime).inWholeMilliseconds
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val buffers = dataLocations
        .associateWith { Buffer(it) }

    @Volatile private var currentOutputCount = 0L


    //-----------------------------------------------------------------------------------------------------------------
    fun getInitial(locationKey: DataLocation, totalSize: Long): Buffer {
        val buffer = buffers[locationKey]
            ?: throw IllegalArgumentException("Unknown: $locationKey")

        check(! buffer.running)
        buffer.totalSize = totalSize

        return buffer
    }


    fun getRunning(locationKey: DataLocation): Buffer {
        val buffer = buffers[locationKey]
            ?: throw IllegalArgumentException("Unknown: $locationKey")
        check(buffer.running)
        check(! buffer.finished)
        return buffer
    }


    fun nextOutput(nextOutputRecords: Long) {
        currentOutputCount += nextOutputRecords
    }


    fun finish() {
        publishUpdate()

        val finishedFiles = buffers.filter { it.value.finished }.map { it.key }
        val runningFiles = buffers.filter { it.value.running }.map { it.key }
        val notStartedFiles = buffers.filter { ! it.value.finished && ! it.value.running }.map { it.key }

        logger.info("Finished all: finished {} - running {} - not started {}",
            finishedFiles, runningFiles, notStartedFiles)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun current(): ReportProgress {
        val snapshot = buffers
            .mapValues { it.value.snapshot() }

        return ReportProgress(
            currentOutputCount, snapshot)
    }


    private fun publishAndLogStarted(locationKey: DataLocation, totalSize: Long) {
        val published = publishUpdate()
        if (published == null) {
            logger.info("Started after error: {}", locationKey)
        }
        else {
            val message = published.inputs[locationKey]!!.toMessage(totalSize)
            logger.info("Started {} ({}) - {}", locationKey, FormatUtils.decimalSeparator(totalSize), message)
        }
    }


    private fun publishAndLogFinished(locationKey: DataLocation, totalSize: Long) {
        val published = publishUpdate()
        if (published == null) {
            logger.info("Finished after error: {}", locationKey)
        }
        else {
            val message = published.inputs[locationKey]!!.toMessage(totalSize)
            logger.info("Finished {} - {}", locationKey, message)
        }
    }


    private fun publishAndLogProcessed(locationKey: DataLocation, totalSize: Long) {
        val published = publishUpdate()
        if (published == null) {
            logger.info("Progress after error: {}", locationKey)
        }
        else {
            val message = published.inputs[locationKey]!!.toMessage(totalSize)
            logger.info("{} - {}", locationKey, message)
        }
    }


    private fun publishUpdate(): ReportProgress? {
        var published: ReportProgress? = null
        taskHandle!!.update { previous ->
            val current = current()
            published = current
            previous!!.withDetail(ExecutionValue.of(
                current.toTaskProgress().toCollection()))
        }
        return published
    }
}