package tech.kzen.auto.server.objects.process

import com.google.common.base.Stopwatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.process.ColumnFilterType
import tech.kzen.auto.common.objects.document.process.FilterSpec
import tech.kzen.auto.common.objects.document.process.OutputInfo
import tech.kzen.auto.common.objects.document.process.PivotSpec
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
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
import tech.kzen.auto.server.objects.process.pivot.row.value.store.FileIndexedStoreOffset
import tech.kzen.auto.server.objects.process.pivot.row.value.store.FileIndexedTextStore
import tech.kzen.auto.server.objects.process.pivot.stats.BufferedValueStatistics
import tech.kzen.auto.server.objects.process.pivot.stats.map.MapValueStatistics
import tech.kzen.auto.server.objects.process.pivot.stats.store.FileValueStatisticsStore
import tech.kzen.auto.server.objects.process.stream.RecordStream
import tech.kzen.auto.util.AutoJvmUtils
import tech.kzen.auto.util.WorkUtils
import java.io.BufferedWriter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit


object ApplyProcessAction
{
    //-----------------------------------------------------------------------------------------------------------------
    private val logger = LoggerFactory.getLogger(ApplyProcessAction::class.java)

    private val modifiedFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

//    private const val progressItems = 1_000
    private const val progressItems = 10_000
//    private const val progressItems = 250_000


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun lookupOutput(
        outputPath: Path
    ): ExecutionResult {
        val absolutePath = outputPath.normalize().toAbsolutePath().toString()

        val info =
            if (! Files.exists(outputPath)) {
                OutputInfo(
                    absolutePath,
                    null,
                    Files.exists(outputPath.parent))
            }
            else {
                val fileTime = withContext(Dispatchers.IO) {
                    Files.getLastModifiedTime(outputPath)
                }

                val formattedTime = fileTime
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(modifiedFormatter)

                OutputInfo(absolutePath, formattedTime, true)
            }

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(info.toCollection()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun applyProcessAsync(
        inputPaths: List<Path>,
        columnNames: List<String>,
        outputPath: Path,
        filterSpec: FilterSpec,
        pivotSpec: PivotSpec,
        handle: TaskHandle
    ): ExecutionResult {
        logger.info("Starting: $outputPath | $filterSpec | $inputPaths")

        val outputValue = ExecutionValue.of(outputPath.toString())
        val progress = TaskProgress.ofNotStarted(
            inputPaths.map { it.fileName.toString() })
        handle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(progress.toCollection())))

        if (! Files.isDirectory(outputPath.parent)) {
            withContext(Dispatchers.IO) {
                Files.createDirectories(outputPath.parent)
            }
        }

        Thread {
            processSync(
                inputPaths, columnNames, outputPath, filterSpec, pivotSpec, handle, progress, outputValue)
        }.start()

        logger.info("Done: $outputPath | $filterSpec | $inputPaths")

        return ExecutionSuccess.ofValue(
            outputValue)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun processSync(
        inputPaths: List<Path>,
        columnNames: List<String>,
        outputPath: Path,
        filterSpec: FilterSpec,
        pivotSpec: PivotSpec,
        handle: TaskHandle,
        progress: TaskProgress,
        outputValue: ExecutionValue
    ) {
        val tempName = AutoJvmUtils.sanitizeFilename(LocalTime.now().toString())
        val tempDir = WorkUtils.resolve(Path.of("data-process", tempName))

        try {
            Files.createDirectories(tempDir)
            processSyncChecked(
                inputPaths, columnNames, outputPath, filterSpec, pivotSpec, handle, progress, outputValue, tempDir)
        }
        catch (e: Exception) {
            logger.warn("Data processing failed", e)
            handle.complete(
                ExecutionFailure(
                    "Unable to process: ${e.message}"))
            return
        }
        finally {
            cleanupTempDir(tempDir)
        }

        handle.complete(
            ExecutionSuccess.ofValue(
                outputValue))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun processSyncChecked(
        inputPaths: List<Path>,
        columnNames: List<String>,
        outputPath: Path,
        filterSpec: FilterSpec,
        pivotSpec: PivotSpec,
        handle: TaskHandle,
        progress: TaskProgress,
        outputValue: ExecutionValue,
        tempDir: Path
    ) {
        if (pivotSpec.isEmpty()) {
            filterSyncChecked(
                inputPaths, columnNames, outputPath, filterSpec, handle, progress, outputValue)
        }
        else {
            pivotSyncChecked(
                inputPaths, columnNames, outputPath, filterSpec, pivotSpec, handle, progress, outputValue, tempDir)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun filterSyncChecked(
        inputPaths: List<Path>,
        columnNames: List<String>,
        outputPath: Path,
        filterSpec: FilterSpec,
        handle: TaskHandle,
        progress: TaskProgress,
        outputValue: ExecutionValue
    ) {
        val filterColumns = columnNames.intersect(filterSpec.columns.keys).toList()

        var nextProgress = progress

        Files.newBufferedWriter(outputPath).use { output ->
            var first = true
            for (inputPath in inputPaths) {
                logger.info("Reading: $inputPath")

                FileStreamer.open(inputPath)!!.use { stream ->
                    nextProgress = filterStream(
                        stream,
                        output,
                        columnNames,
                        filterColumns,
                        filterSpec,
                        first,
                        nextProgress,
                        inputPath,
                        outputValue,
                        handle
                    )
                }

                first = false
            }
        }
    }


    // TODO: refactor (too many arguments)
    private fun filterStream(
        input: RecordStream,
        output: BufferedWriter,
        columnNames: List<String>,
        filterColumns: List<String>,
        filterSpec: FilterSpec,
        writHeader: Boolean,
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

        val csvPrinter = CSVFormat.DEFAULT.print(output)

        if (writHeader) {
            csvPrinter.printRecord(columnNames)
        }

        var count: Long = 0
        var writtenCount: Long = 0

        next_record@
        while (input.hasNext() && ! handle.cancelRequested()) {
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
                val columnCriteria = filterSpec.columns[filterColumn]!!

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

            val values = record.getAll(columnNames)
            csvPrinter.printRecord(values)
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
        inputPaths: List<Path>,
        columnNames: List<String>,
        outputPath: Path,
        filterSpec: FilterSpec,
        pivotSpec: PivotSpec,
        handle: TaskHandle,
        progress: TaskProgress,
        outputValue: ExecutionValue,
        tempDir: Path
    ) {
//        val source = MultiRecordSource(inputPaths)

        val filterColumns = columnNames.intersect(filterSpec.columns.keys).toList()

        val pivotBuilder =
//            memoryPivot(pivotSpec)
            filePivot(pivotSpec, tempDir)

        pivotBuilder.use {
            var nextProgress = progress

            for (inputPath in inputPaths) {
                logger.info("Reading: $inputPath")

                FileStreamer.open(inputPath)!!.use { stream ->
                    nextProgress = pivotStream(
                        stream,
                        filterColumns,
                        filterSpec,
                        pivotBuilder,
                        nextProgress,
                        inputPath,
                        outputValue,
                        handle
                    )
                }
            }

            Files.newBufferedWriter(outputPath).use { output ->
                val csvPrinter = CSVFormat.DEFAULT.print(output)

                pivotBuilder.view().use { pivotView ->
                    csvPrinter.printRecord(pivotView.header())

                    while (pivotView.hasNext()) {
                        val pivotRecord = pivotView.next()
                        val pivotValues = pivotRecord.getAll(pivotView.header())
                        csvPrinter.printRecord(pivotValues)
                    }
                }
            }
        }
    }


    private fun pivotStream(
        input: RecordStream,
        filterColumns: List<String>,
        filterSpec: FilterSpec,
        pivotBuilder: PivotBuilder,
        previousProgress: TaskProgress,
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
                val columnCriteria = filterSpec.columns[filterColumn]!!

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
            pivotSpec,
            RowIndex(
                MapRowValueIndex(),
                MapRowSignatureIndex()),
            MapValueStatistics(
                pivotSpec.values.size)
        )
    }


    private fun filePivot(pivotSpec: PivotSpec, tempDir: Path): PivotBuilder {
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

        val indexedTextStore = BufferedIndexedTextStore(
            FileIndexedTextStore(
                rowTextContentFile,
                FileIndexedStoreOffset(rowTextIndexFile)))

        val rowValueIndex = StoreRowValueIndex(
            rowValueDigestIndex, indexedTextStore)

        val rowSignatureDigestIndex =
//            FileDigestIndex(rowSignatureDigestDir)
//            MapDbDigestIndex(rowSignatureDigestDir)
            H2DigestIndex(rowSignatureDigestDir)

        val indexedSignatureStore = BufferedIndexedSignatureStore(
            FileIndexedSignatureStore(
                rowSignatureFile,
                pivotSpec.rows.size))

        val rowSignatureIndex = StoreRowSignatureIndex(
            rowSignatureDigestIndex, indexedSignatureStore)

        val valueStatistics = BufferedValueStatistics(
            FileValueStatisticsStore(
                valueStatisticsFile,
                pivotSpec.values.size
            ))

        return PivotBuilder(
            pivotSpec,
            RowIndex(rowValueIndex, rowSignatureIndex),
            valueStatistics)
    }


    private fun cleanupTempDir(tempDir: Path) {
        try {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete)
        }
        catch (e: Exception) {
            logger.error("Unable to cleanup", e)
        }
    }
}