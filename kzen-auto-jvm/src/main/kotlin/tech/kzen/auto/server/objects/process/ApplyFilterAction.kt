package tech.kzen.auto.server.objects.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.process.ColumnCriteriaType
import tech.kzen.auto.common.objects.document.process.CriteriaSpec
import tech.kzen.auto.common.objects.document.process.OutputInfo
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.server.objects.process.model.RecordStream
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter


object ApplyFilterAction
{
    //-----------------------------------------------------------------------------------------------------------------
    private val logger = LoggerFactory.getLogger(ApplyFilterAction::class.java)

    private val modifiedFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


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
    suspend fun applyFilterAsync(
        inputPaths: List<Path>,
        columnNames: List<String>,
        outputPath: Path,
        criteriaSpec: CriteriaSpec,
        handle: TaskHandle
    ): ExecutionResult {
        logger.info("Starting: $outputPath | $criteriaSpec | $inputPaths")

        val outputValue = ExecutionValue.of(outputPath.toString())
        var progress = TaskProgress.ofNotStarted(
            inputPaths.map { it.fileName.toString() })
        handle.update(ExecutionSuccess(
            outputValue,
            ExecutionValue.of(progress.toCollection())))

        if (! Files.isDirectory(outputPath.parent)) {
            withContext(Dispatchers.IO) {
                Files.createDirectories(outputPath.parent)
            }
        }

        val filterColumns = columnNames.intersect(criteriaSpec.columns.keys).toList()

        Thread {
            Files.newBufferedWriter(outputPath).use { output ->
                var first = true
                for (inputPath in inputPaths) {
                    logger.info("Reading: $inputPath")

                    FileStreamer.open(inputPath)!!.use { stream ->
                        progress = filterStream(
                            stream,
                            output,
                            columnNames,
                            filterColumns,
                            criteriaSpec,
                            first,
                            progress,
                            inputPath,
                            outputValue,
                            handle
                        )
                    }

                    first = false
                }
            }

            handle.complete(
                ExecutionSuccess.ofValue(
                    outputValue))
        }.start()

        logger.info("Done: $outputPath | $criteriaSpec | $inputPaths")

        return ExecutionSuccess.ofValue(
            outputValue)
    }


    // TODO: refactor (too many arguments)
    private fun filterStream(
        input: RecordStream,
        output: BufferedWriter,
        columnNames: List<String>,
        filterColumns: List<String>,
        criteriaSpec: CriteriaSpec,
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
            if (count % 250_000 == 0L) {
                val progressMessage = "Processed ${ColumnSummaryAction.formatCount( count)}, " +
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
                val columnCriteria = criteriaSpec.columns[filterColumn]!!

                if (columnCriteria.values.isNotEmpty()) {
                    val present = columnCriteria.values.contains(value)

                    val allow =
                        when (columnCriteria.type) {
                            ColumnCriteriaType.RequireAny ->
                                present

                            ColumnCriteriaType.ExcludeAll ->
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
}