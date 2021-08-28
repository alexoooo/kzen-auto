package tech.kzen.auto.server.objects.pipeline.exec

import com.google.common.base.Stopwatch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.progress.ReportFileProgress
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime


class PipelineTrace(
    private val logicTraceHandle: LogicTraceHandle,
    private val locationKey: DataLocation,
    private val totalSize: Long
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTrace::class.java)

        private const val progressBytesWatermark = 1024L * 1024

        private val clock: Clock = Clock.System
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val innerStopwatch = Stopwatch.createUnstarted()
    private val logicTracePath = PipelineConventions.inputTracePath(locationKey)

    @Volatile var startTime = Instant.DISTANT_PAST
    @Volatile var endTime = Instant.DISTANT_PAST

    @Volatile var running = false
    @Volatile var finished = false
    @Volatile var records: Long = 0
    @Volatile var readBytes: Long = 0
    @Volatile var uncompressedBytes: Long = 0
    @Volatile var recentBytesPerSecond: Long = 0
    @Volatile var recentRecordsPerSecond: Long = 0

    @Volatile var progressBytesRemaining: Long = 0
    @Volatile var previousUncompressedBytes: Long = 0
    @Volatile var previousRecords: Long = 0


    fun startReading() {
        check(! running)

        running = true
        startTime = clock.now()
        innerStopwatch.start()

        publishAndLogStarted()
    }


    fun finishParsing() {
        check(running)
        check(! finished)

        running = false
        finished = true
        innerStopwatch.reset()
        endTime = clock.now()

        publishAndLogFinished()
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

            publishAndLogProcessed()
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun publishAndLogStarted() {
        val published = publishUpdate()
        val message = published.toMessage(totalSize)
        logger.info("Started {} ({}) - {}", locationKey, FormatUtils.decimalSeparator(totalSize), message)
    }


    private fun publishAndLogFinished() {
        val published = publishUpdate()
        val message = published.toMessage(totalSize)
        logger.info("Finished {} - {}", locationKey, message)
    }


    private fun publishAndLogProcessed() {
        val published = publishUpdate()
        val message = published.toMessage(totalSize)
        logger.info("{} - {}", locationKey, message)
    }


    private fun publishUpdate(): ReportFileProgress {
        val snapshot = snapshot()
        logicTraceHandle.set(logicTracePath, ExecutionValue.of(
            snapshot.toCollection()
        ))
        return snapshot
    }
}