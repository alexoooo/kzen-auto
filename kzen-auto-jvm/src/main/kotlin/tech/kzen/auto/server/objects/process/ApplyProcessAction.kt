package tech.kzen.auto.server.objects.process

import com.google.common.base.Stopwatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.process.*
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.auto.server.objects.process.filter.IndexedCsvTable
import tech.kzen.auto.server.objects.process.model.ProcessRunSignature
import tech.kzen.auto.server.objects.process.model.ProcessRunSpec
import tech.kzen.auto.server.objects.process.pivot.PivotBuilder
import tech.kzen.auto.server.objects.process.pivot.row.RowIndex
import tech.kzen.auto.server.objects.process.pivot.row.digest.H2DigestIndex
import tech.kzen.auto.server.objects.process.pivot.row.signature.MapRowSignatureIndex
import tech.kzen.auto.server.objects.process.pivot.row.signature.StoreRowSignatureIndex
import tech.kzen.auto.server.objects.process.pivot.row.signature.store.BufferedIndexedSignatureStore
import tech.kzen.auto.server.objects.process.pivot.row.signature.store.FileIndexedSignatureStore
import tech.kzen.auto.server.objects.process.pivot.row.value.MapRowValueIndex
import tech.kzen.auto.server.objects.process.pivot.row.value.StoreRowValueIndex
import tech.kzen.auto.server.objects.process.pivot.row.value.store.BufferedIndexedTextStore
import tech.kzen.auto.server.objects.process.pivot.row.value.store.FileIndexedTextStore
import tech.kzen.auto.server.objects.process.pivot.stats.BufferedValueStatistics
import tech.kzen.auto.server.objects.process.pivot.stats.map.MapValueStatistics
import tech.kzen.auto.server.objects.process.pivot.stats.store.FileValueStatisticsStore
import tech.kzen.auto.server.objects.process.pivot.store.BufferedOffsetStore
import tech.kzen.auto.server.objects.process.pivot.store.FileOffsetStore
import tech.kzen.auto.server.objects.process.stream.RecordStream
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.model.locate.ObjectLocation
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit


// TODO: https://stackoverflow.com/questions/3335969/reading-a-gzip-file-from-a-filechannel-java-nio
// https://javadoc.io/static/com.conversantmedia/disruptor/1.2.9/com/conversantmedia/util/concurrent/PushPullConcurrentQueue.html
// http://lmax-exchange.github.io/disruptor/user-guide/index.html
object ApplyProcessAction
{
    //-----------------------------------------------------------------------------------------------------------------
    private val logger = LoggerFactory.getLogger(ApplyProcessAction::class.java)

    private val modifiedFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

//    private const val progressItems = 1_000
    private const val progressItems = 10_000
//    private const val progressItems = 250_000


    private data class SaveInfo(
        val path: Path,
        val defaultPath: Path,
        val custom: Boolean,
        val customInvalid: Boolean,
        val message: String
    )


    //-----------------------------------------------------------------------------------------------------------------
    private fun formatTime(instant: Instant): String {
        return instant
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(modifiedFormatter)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun lookupOutput(
        objectLocation: ObjectLocation,
        runSpec: ProcessRunSpec,
        runDir: Path,
        outputSpec: OutputSpec
    ): ExecutionResult {
        val runSignature = runSpec.toSignature()

        val info =
            if (! Files.exists(runDir)) {
                val columnNames =
                    if (runSignature.hasPivot()) {
                        PivotBuilder.ExportSignature.of(
                            runSpec.pivot.rows.toList(),
                            runSpec.pivot.values
                        ).header
                    }
                    else {
                        runSignature.columnNames
                    }

                OutputInfo(
                    "Missing, will be created: $runDir",
                    null,
                    0L,
                    OutputPreview(columnNames, listOf(), 0L)
                )
            }
            else {
                val taskId = ServerContext
                    .modelTaskRepository
                    .lookupActive(objectLocation)
                    .singleOrNull()
                if (taskId != null) {
                    val request =
                        if (runSignature.hasPivot()) {
                            DetachedRequest(
                                RequestParams(
                                    runSpec.pivot.values.toPreviewRequest().parameters.values +
                                    outputSpec.toPreviewRequest().parameters.values
                                ),
                                null
                            )
                        }
                        else {
                            outputSpec.toPreviewRequest()
                        }

                    val result = ServerContext
                        .modelTaskRepository
                        .request(taskId, request)

                    if (result != null) {
                        return result
                    }
                }

                val fileTime = withContext(Dispatchers.IO) {
                    Files.getLastModifiedTime(runDir)
                }

                val formattedTime = formatTime(fileTime.toInstant())

                val zeroBasedPreview = outputSpec.previewStartZeroBased()

                val rowCount: Long
                val preview =
                    if (! runSignature.hasPivot()) {
                        IndexedCsvTable(runSignature.columnNames, runDir).use {
                            rowCount = it.rowCount()
                            it.preview(zeroBasedPreview, outputSpec.previewCount)
                        }
                    }
                    else {
                        filePivot(runSpec.pivot.rows, runSpec.pivot.values.columns.keys, runDir).use {
                            rowCount = it.rowCount()
                            it.preview(runSpec.pivot.values, zeroBasedPreview, outputSpec.previewCount)
                        }
                    }

                val saveInfo = saveInfo(runDir, outputSpec)

                OutputInfo(
                    saveInfo.message,
                    formattedTime,
                    rowCount,
                    preview)
            }

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(info.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun saveOutput(
        runSpec: ProcessRunSpec,
        runDir: Path,
        outputSpec: OutputSpec
    ): ExecutionResult {
        if (! Files.exists(runDir)) {
            return ExecutionFailure("Not found: $runDir")
        }

        val runSignature = runSpec.toSignature()

        val saveInfo = saveInfo(runDir, outputSpec)
        val outputPath = saveInfo.path

        val successValue = ExecutionSuccess(
            ExecutionValue.of(outputPath.toString()),
            NullExecutionValue)

        if (! runSignature.hasPivot() &&
            saveInfo.path.toAbsolutePath().normalize() ==
                saveInfo.defaultPath.toAbsolutePath().normalize())
        {
            // NB: don't override source of truth
            return successValue
        }

        Files.newBufferedWriter(outputPath).use { output ->
            val csvPrinter = CSVFormat.DEFAULT.print(output)

            if (! runSignature.hasPivot()) {
                IndexedCsvTable(runSignature.columnNames, runDir).use {
                    it.traverseWithHeader { row ->
                        csvPrinter.printRecord(row)
                    }
                }
            }
            else {
                filePivot(runSpec.pivot.rows, runSpec.pivot.values.columns.keys, runDir).use {
                    it.traverseWithHeader(runSpec.pivot.values) { row ->
                        csvPrinter.printRecord(row)
                    }
                }
            }
        }

        return successValue
    }


    private fun saveInfo(runDir: Path, outputSpec: OutputSpec): SaveInfo {
        val defaultPath = runDir.resolve(IndexedCsvTable.tableFile)

        val customPath: Path?
        val customInvalid: Boolean
        if (outputSpec.savePath.isBlank()) {
            customPath = null
            customInvalid = false
        }
        else {
            customPath = try {
                Paths.get(outputSpec.savePath)
            } catch (e: InvalidPathException) {
                null
            }
            customInvalid = customPath == null
        }

        val pathWithFallback = customPath ?: defaultPath

        val existsMessage =
            if (Files.exists(pathWithFallback)) {
                "already exists"
            }
            else {
                "does not exist (will create)"
            }

        val typeMessage = when {
            customInvalid ->
                "Invalid path provided (using default)"

            customPath == null ->
                "Using default path"

            else ->
                "Using custom path"
        }

        val message = "$typeMessage, $existsMessage: ${pathWithFallback.toAbsolutePath().normalize()}"

        return SaveInfo(
            pathWithFallback,
            defaultPath,
            customPath != null || customInvalid,
            customInvalid,
            message)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun applyProcessAsync(
        runSpec: ProcessRunSpec,
        runDir: Path,
        handle: TaskHandle
    ): ExecutionResult {
        logger.info("Starting: $runDir | $runSpec")

        // TODO: value summary and output preview
        val outputValue = ExecutionValue.of(runDir.toString())

        val progress = TaskProgress.ofNotStarted(
            runSpec.inputs.map { it.fileName.toString() })
        handle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(progress.toCollection())))

        Thread {
            processSync(
                runSpec, runDir, handle, progress, outputValue)
        }.start()

        logger.info("Done: $runDir | $runSpec")

        return ExecutionSuccess.ofValue(
            outputValue)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun processSync(
        runSignature: ProcessRunSpec,
        runDir: Path,
        handle: TaskHandle,
        progress: TaskProgress,
        outputValue: ExecutionValue
    ) {
        try {
            processSyncChecked(
                runSignature, runDir, handle, progress, outputValue)
        }
        catch (e: Exception) {
            logger.warn("Data processing failed", e)
            handle.complete(
                ExecutionFailure(
                    "Unable to process: ${e.message}"))
            return
        }

        handle.complete(
            ExecutionSuccess.ofValue(
                outputValue))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun processSyncChecked(
        runSpec: ProcessRunSpec,
        runDir: Path,
        handle: TaskHandle,
        progress: TaskProgress,
        outputValue: ExecutionValue
    ) {
        val runSignature = runSpec.toSignature()
        if (! runSignature.hasPivot()) {
            filterSyncChecked(
                runSignature, runDir, handle, progress, outputValue)
        }
        else {
            pivotSyncChecked(
                runSpec, runDir, handle, progress, outputValue)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun filterSyncChecked(
        runSignature: ProcessRunSignature,
        runDir: Path,
        handle: TaskHandle,
        progress: TaskProgress,
        outputValue: ExecutionValue
    ) {
        val filterColumns = runSignature.columnNames
            .intersect(runSignature.nonEmptyFilter.columns.keys).toList()

        val tableBuilder = IndexedCsvTable(
            runSignature.columnNames, runDir)

        var nextProgress = progress

        tableBuilder.use { output ->
            for (inputPath in runSignature.inputs) {
                logger.info("Reading: $inputPath")

                FileStreamer.open(inputPath)!!.use { stream ->
                    nextProgress = filterStream(
                        stream,
                        output,
                        runSignature,
                        filterColumns,
                        nextProgress,
                        inputPath,
                        outputValue,
                        handle
                    )
                }
            }
        }
    }


    // TODO: refactor (too many arguments)
    private fun filterStream(
        input: RecordStream,
        output: IndexedCsvTable,
        runSignature: ProcessRunSignature,
        filterColumns: List<String>,
        previousProgress: TaskProgress,
        inputPath: Path,
        outputValue: ExecutionValue,
        handle: TaskHandle
    ): TaskProgress {
        var nextProgress = previousProgress.update(
            inputPath.fileName.toString(),
            "Started filtering")
        handle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(nextProgress.toCollection())))

        var count: Long = 0
        var writtenCount: Long = 0

        next_record@
        while (input.hasNext() && ! handle.cancelRequested()) {
            handle.processRequests { request ->
                val outputSpec = OutputSpec.ofPreviewRequest(request)
                val zeroBasedPreviewStart = outputSpec.previewStartZeroBased()
                val preview = output.preview(zeroBasedPreviewStart, outputSpec.previewCount)

                val outputInfo = OutputInfo(
                    "Running: ${output.outputPath().toAbsolutePath().normalize()}",
                    formatTime(output.modified()),
                    output.rowCount(),
                    preview)

                ExecutionSuccess(
                    ExecutionValue.of(
                        outputInfo.toCollection()),
                    NullExecutionValue)
            }

            val record = input.next()

            count++
            if (count % progressItems == 0L) {
                val progressMessage = "Filtered ${ColumnSummaryAction.formatCount( count )}, " +
                        "wrote ${ColumnSummaryAction.formatCount(writtenCount)}"
                logger.info(progressMessage)

                nextProgress = nextProgress.update(
                    inputPath.fileName.toString(),
                    progressMessage)
                handle.update(ExecutionSuccess(
                    outputValue,
                    ExecutionValue.of(nextProgress.toCollection())))
            }

            for (filterColumn in filterColumns) {
                val value = record.get(filterColumn)

                @Suppress("MapGetWithNotNullAssertionOperator")
                val columnCriteria = runSignature.nonEmptyFilter.columns[filterColumn]!!

                if (columnCriteria.values.isNotEmpty()) {
                    val present = columnCriteria.values.contains(value)

                    val allow =
                        when (columnCriteria.type) {
                            ColumnFilterType.RequireAny ->
                                present

                            ColumnFilterType.ExcludeAll ->
                                ! present
                        }

                    if (! allow) {
                        continue@next_record
                    }
                }
            }

            writtenCount++

            val values = record.getOrEmptyAll(runSignature.columnNames)
            output.add(values)
        }

        if (handle.cancelRequested()) {
            nextProgress = nextProgress.update(
                inputPath.fileName.toString(),
                "Interrupted: " + ColumnSummaryAction.formatCount(count) +
                        ", wrote " + ColumnSummaryAction.formatCount(writtenCount))
            logger.info("Cancelled file filter: {}", ColumnSummaryAction.formatCount(count))
        }
        else {
            nextProgress = nextProgress.update(
                inputPath.fileName.toString(),
                "Finished " + ColumnSummaryAction.formatCount(count) +
                        ", wrote " + ColumnSummaryAction.formatCount(writtenCount)
            )
            logger.info("Finished file filter: {}", ColumnSummaryAction.formatCount(count))
        }
        handle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(nextProgress.toCollection())))

        return nextProgress
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun pivotSyncChecked(
        runSignature: ProcessRunSpec,
        runDir: Path,
        handle: TaskHandle,
        progress: TaskProgress,
        outputValue: ExecutionValue
    ) {
//        val outputPath = runDir.resolve("output.csv")

        val filterColumns = runSignature.columnNames
            .intersect(runSignature.filter.columns.keys)
            .toList()

        val pivotBuilder =
//            memoryPivot(pivotSpec)
            filePivot(runSignature.pivot.rows, runSignature.pivot.values.columns.keys, runDir)

        pivotBuilder.use {
            var nextProgress = progress

            for (inputPath in runSignature.inputs) {
                logger.info("Reading: $inputPath")

                FileStreamer.open(inputPath)!!.use { stream ->
                    nextProgress = pivotStream(
                        stream,
                        filterColumns,
                        runSignature,
                        pivotBuilder,
                        nextProgress,
                        runDir,
                        inputPath,
                        outputValue,
                        handle
                    )
                }
            }
        }
    }


    private fun pivotStream(
        input: RecordStream,
        filterColumns: List<String>,
        runSignature: ProcessRunSpec,
        pivotBuilder: PivotBuilder,
        previousProgress: TaskProgress,
        runDir: Path,
        inputPath: Path,
        outputValue: ExecutionValue,
        handle: TaskHandle
    ): TaskProgress {
        var nextProgress = previousProgress.update(
            inputPath.fileName.toString(),
            "Started pivoting")
        handle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(nextProgress.toCollection())))

        var count: Long = 0
        var pivotCount: Long = 0
        val outerStopwatch = Stopwatch.createStarted()
        val innerStopwatch = Stopwatch.createStarted()

        next_record@
        while (input.hasNext() && ! handle.cancelRequested()) {
            handle.processRequests { request ->
                val outputSpec = OutputSpec.ofPreviewRequest(request)
                val zeroBasedPreviewStart = outputSpec.previewStartZeroBased()
                val valueTableSpec = PivotValueTableSpec.ofPreviewRequest(request)

                val preview = pivotBuilder.preview(
                    valueTableSpec, zeroBasedPreviewStart, outputSpec.previewCount)

                val outputInfo = OutputInfo(
                    "Running: $runDir",
                    formatTime(Instant.now()),
                    pivotBuilder.rowCount(),
                    preview)

                ExecutionSuccess(
                    ExecutionValue.of(
                        outputInfo.toCollection()),
                    NullExecutionValue)
            }

            val record = input.next()

            if (count != 0L && count % progressItems == 0L) {
                val progressMessage =
                    "Processed ${ColumnSummaryAction.formatCount(count)}, " +
                    "pivoted ${ColumnSummaryAction.formatCount(pivotCount)} " +
                    "at ${ColumnSummaryAction.formatCount(
                        (1000.0 * progressItems / innerStopwatch.elapsed(TimeUnit.MILLISECONDS)).toLong())}/s"
                innerStopwatch.reset().start()

                logger.info(progressMessage)

                nextProgress = nextProgress.update(
                    inputPath.fileName.toString(),
                    progressMessage)
                handle.update(ExecutionSuccess(
                    outputValue,
                    ExecutionValue.of(nextProgress.toCollection())))
            }
            count++

            for (filterColumn in filterColumns) {
                val value = record.get(filterColumn)

                @Suppress("MapGetWithNotNullAssertionOperator")
                val columnCriteria = runSignature.filter.columns[filterColumn]!!

                if (columnCriteria.values.isNotEmpty()) {
                    val present = columnCriteria.values.contains(value)

                    val allow =
                        when (columnCriteria.type) {
                            ColumnFilterType.RequireAny ->
                                present

                            ColumnFilterType.ExcludeAll ->
                                ! present
                        }

                    if (! allow) {
                        continue@next_record
                    }
                }
            }

            pivotCount++
            pivotBuilder.add(record)
        }

        val speedMessage =
            "at ${ColumnSummaryAction.formatCount(
                (1000.0 * count / outerStopwatch.elapsed(TimeUnit.MILLISECONDS)).toLong())}/s overall"
        if (handle.cancelRequested()) {
            val message =
                "Interrupted: " + ColumnSummaryAction.formatCount(count) +
                ", wrote " + ColumnSummaryAction.formatCount(pivotCount) +
                " $speedMessage"
            nextProgress = nextProgress.update(inputPath.fileName.toString(), message)
            logger.info(message)
        }
        else {
            val message =
                "Finished " + ColumnSummaryAction.formatCount(count) +
                ", wrote " + ColumnSummaryAction.formatCount(pivotCount) +
                " $speedMessage"
            nextProgress = nextProgress.update(inputPath.fileName.toString(), message)
            logger.info(message)
        }

        handle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(nextProgress.toCollection())))

        return nextProgress
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun memoryPivot(pivotSpec: PivotSpec): PivotBuilder {
        return PivotBuilder(
            pivotSpec.rows,
            pivotSpec.values.columns.keys,
            RowIndex(
                MapRowValueIndex(),
                MapRowSignatureIndex()),
            MapValueStatistics(
                pivotSpec.values.columns.size)
        )
    }


    private fun filePivot(
        rows: Set<String>,
        values: Set<String>,
        tempDir: Path
    ): PivotBuilder {
        val rowTextContentFile = tempDir.resolve("row-text-value.bin")
        val rowTextIndexFile= tempDir.resolve("row-text-index.bin")
        val rowSignatureFile = tempDir.resolve("row-signature.bin")
        val valueStatisticsFile = tempDir.resolve("value-statistics.bin")
        val rowValueDigestDir = tempDir.resolve("row-text-digest")
        val rowSignatureDigestDir = tempDir.resolve("row-signature-digest")

        val rowValueDigestIndex =
//            FileDigestIndex(rowValueDigestDir)
//            MapDbDigestIndex(rowValueDigestDir)
            H2DigestIndex(rowValueDigestDir)

        val textOffsetStore = BufferedOffsetStore(
            FileOffsetStore(rowTextIndexFile))
        val indexedTextStore = BufferedIndexedTextStore(
            FileIndexedTextStore(
                rowTextContentFile,
                textOffsetStore
            ))

        val rowValueIndex = StoreRowValueIndex(
            rowValueDigestIndex, indexedTextStore)

        val rowSignatureDigestIndex =
//            FileDigestIndex(rowSignatureDigestDir)
//            MapDbDigestIndex(rowSignatureDigestDir)
            H2DigestIndex(rowSignatureDigestDir)

        val indexedSignatureStore = BufferedIndexedSignatureStore(
            FileIndexedSignatureStore(
                rowSignatureFile,
                rows.size))

        val rowSignatureIndex = StoreRowSignatureIndex(
            rowSignatureDigestIndex, indexedSignatureStore)

        val valueStatistics = BufferedValueStatistics(
            FileValueStatisticsStore(
                valueStatisticsFile,
                values.size
            ))

        return PivotBuilder(
            rows,
            values,
            RowIndex(rowValueIndex, rowSignatureIndex),
            valueStatistics)
    }
}