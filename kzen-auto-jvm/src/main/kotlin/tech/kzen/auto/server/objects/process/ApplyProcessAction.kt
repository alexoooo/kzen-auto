package tech.kzen.auto.server.objects.process

import com.google.common.base.Stopwatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.process.*
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.server.objects.process.filter.IndexedCsvTable
import tech.kzen.auto.server.objects.process.model.ProcessAsyncState
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
import java.nio.file.Files
import java.nio.file.Path
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
        runDir: Path,
        request: DetachedRequest
    ): ExecutionResult {
//        val absolutePath = outputPath.normalize().toAbsolutePath().toString()

        val info =
            if (! Files.exists(runDir)) {
                OutputInfo(
                    runDir.toString(),
                    null,
                    null)
            }
            else {
                val startRow = request.getInt(ProcessConventions.startRowKey) ?: 0
                val rowCount = request.getInt(ProcessConventions.rowCountKey) ?: OutputPreview.defaultRowCount

                val activeState = ServerContext
                    .modelTaskRepository
                    .activeAsyncStates()
                    .values
                    .filterIsInstance<ProcessAsyncState>()
                    .singleOrNull { it.runDir == runDir }


                val fileTime = withContext(Dispatchers.IO) {
                    Files.getLastModifiedTime(runDir)
                }

                val formattedTime = fileTime
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(modifiedFormatter)

                OutputInfo(
                    runDir.toString(),
                    formattedTime,
                    null)
            }

        return ExecutionSuccess.ofValue(
            ExecutionValue.of(info.toCollection()))
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
            .intersect(runSignature.filter.columns.keys).toList()

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
//        writHeader: Boolean,
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

//        val csvPrinter = CSVFormat.DEFAULT.print(output)
//
//        if (writHeader) {
//            csvPrinter.printRecord(runSignature.columnNames)
//        }

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

            writtenCount++

            val values = record.getOrEmptyAll(runSignature.columnNames)
            output.add(values)
//            csvPrinter.printRecord(values)
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
        val outputPath = runDir.resolve("output.csv")

        val filterColumns = runSignature.columnNames
            .intersect(runSignature.filter.columns.keys)
            .toList()

        val pivotBuilder =
//            memoryPivot(pivotSpec)
            filePivot(runSignature.pivot.rows, runSignature.pivot.values.keys, runDir)

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
                        inputPath,
                        outputValue,
                        handle
                    )
                }
            }

            Files.newBufferedWriter(outputPath).use { output ->
                val csvPrinter = CSVFormat.DEFAULT.print(output)

                pivotBuilder.view(
                    runSignature.pivot.values
                ).use { pivotView ->
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
        runSignature: ProcessRunSpec,
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
            pivotSpec.values.keys,
            RowIndex(
                MapRowValueIndex(),
                MapRowSignatureIndex()),
            MapValueStatistics(
                pivotSpec.values.size)
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