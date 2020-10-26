package tech.kzen.auto.server.objects.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.reactive.*
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.server.objects.process.model.ProcessRunSignature
import tech.kzen.auto.server.objects.process.model.ValueSummaryBuilder
import tech.kzen.auto.util.AutoJvmUtils
import java.nio.file.Files
import java.nio.file.Path


object ColumnSummaryAction {
    //-----------------------------------------------------------------------------------------------------------------
    private val logger = LoggerFactory.getLogger(ColumnSummary::class.java)


    private const val summaryCsvFilename = "summary.csv"
    private const val nominalCsvFilename = "nominal.csv"
    private const val numericCsvFilename = "numeric.csv"
    private const val opaqueCsvFilename = "opaque.csv"


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun summarizeAllAsync(
        runSignature: ProcessRunSignature,
        handle: TaskHandle
    ) {
        val notLoaded = mutableListOf<Path>()

        val builder = mutableMapOf<String, ColumnSummary>()

        next_input_path@
        for (inputPath in runSignature.inputs) {
            val indexDir = FilterIndex.inputIndexPath(inputPath)
            if (! Files.isDirectory(indexDir)) {
                notLoaded.add(inputPath)
                continue
            }

            for (columnName in runSignature.columnNames) {
                val columnDirName = AutoJvmUtils.sanitizeFilename(columnName)
                val columnDir = indexDir.resolve(columnDirName)

                if (! Files.exists(columnDir)) {
                    notLoaded.add(inputPath)
                    continue@next_input_path
                }

                val columnSummary = loadValueSummary(columnDir)
                val cumulative = builder.getOrDefault(columnName, ColumnSummary.empty)
                builder[columnName] = ValueSummaryBuilder.merge(cumulative, columnSummary)
            }
        }

        if (notLoaded.isEmpty()) {
            val tableSummary = TableSummary(builder)

            handle.complete(ExecutionSuccess.ofValue(
                ExecutionValue.of(tableSummary.toCollection())))
        }
        else {
            summarizeRemainingAsync(notLoaded, runSignature, builder, handle)
        }
    }


    private fun summarizeRemainingAsync(
        remainingInputPaths: List<Path>,
        runSignature: ProcessRunSignature,
        builder: MutableMap<String, ColumnSummary>,
        handle: TaskHandle
    ) {
        val initialTableSummary = TableSummary(builder)
        var progress = TaskProgress.ofNotStarted(
            remainingInputPaths.map { it.fileName.toString() })
        handle.update(ExecutionSuccess(
            ExecutionValue.of(initialTableSummary.toCollection()),
            ExecutionValue.of(progress.toCollection())))

        Thread {
            val remainingInputPathsBuilder = remainingInputPaths.toMutableList()
            while (remainingInputPathsBuilder.isNotEmpty()) {
                val inputPath = remainingInputPathsBuilder.removeAt(0)

                val (columnSummaries,
                    nextProgress
                ) = buildValueSummariesAsync(
                    inputPath, runSignature.columnNames, builder, progress, handle
                )
                progress = nextProgress

                if (columnSummaries == null) {
                    logger.info("Aborted at: {}", inputPath)
                    break
                }

                for (columnName in runSignature.columnNames) {
                    val columnSummary = columnSummaries[columnName] ?: error("Missing: $columnName")
                    val cumulative = builder.getOrDefault(columnName, ColumnSummary.empty)
                    builder[columnName] = ValueSummaryBuilder.merge(cumulative, columnSummary)
                }
            }

            handle.complete(
                ExecutionSuccess.ofValue(
                    ExecutionValue.of(builder.mapValues { it.value.toCollection() })
                )
            )
        }.start()
    }


    private fun buildValueSummariesAsync(
        inputPath: Path,
        columnNames: List<String>,
        partialResult: Map<String, ColumnSummary>,
        previousProgress: TaskProgress,
        handle: TaskHandle
    ):
            Pair<Map<String, ColumnSummary>?, TaskProgress>
    {
        logger.info("Summarizing columns: {}", inputPath)

        val builders = mutableMapOf<String, ValueSummaryBuilder>()

        var nextProgress = previousProgress.update(
            inputPath.fileName.toString(),
            "Started")
        handle.update(ExecutionSuccess(
            ExecutionValue.of(TableSummary(partialResult).toCollection()),
            ExecutionValue.of(nextProgress.toCollection())))

        FileStreamer.open(inputPath)!!.use { stream ->
            for (columnName in stream.header()) {
                builders[columnName] = ValueSummaryBuilder()
            }

            var count: Long = 0
            while (stream.hasNext() && ! handle.cancelRequested()) {
                count++
                if (count % 250_000 == 0L) {
                    val intermittentPartialResults = columnNames.map {
                        it to ValueSummaryBuilder.merge(
                            (partialResult[it] ?: ColumnSummary.empty),
                            builders[it]!!.build())
                    }.toMap()

                    val updatedTableSummary = TableSummary(intermittentPartialResults)
                    nextProgress = nextProgress.update(
                        inputPath.fileName.toString(),
                        formatCount(count))
                    handle.update(ExecutionSuccess(
                        ExecutionValue.of(updatedTableSummary.toCollection()),
                        ExecutionValue.of(nextProgress.toCollection())))

                    logger.info("Summarized: {}", formatCount(count))
                }

                val record = stream.next()
                for (columnName in stream.header()) {
                    val value = record.get(columnName)!!
                    builders[columnName]!!.add(value)
                }
            }

            if (handle.cancelRequested()) {
                nextProgress = nextProgress.update(
                    inputPath.fileName.toString(),
                    "Interrupted: " + formatCount(count))
                logger.info("Cancelled file summary: {}", formatCount(count))
            }
            else {
                runBlocking {
                    val indexDir = FilterIndex.inputIndexPath(inputPath)
                    for (e in builders) {
                        val saveColumnDirName = AutoJvmUtils.sanitizeFilename(e.key)
                        val saveColumnDir = indexDir.resolve(saveColumnDirName)
                        saveValueSummary(e.value.build(), saveColumnDir)
                    }
                }

                nextProgress = nextProgress.update(
                    inputPath.fileName.toString(),
                    "Done: " + formatCount(count))
                logger.info("Finished file summary: {}", formatCount(count))
            }

            val combinedResults = columnNames.map {
                it to ValueSummaryBuilder.merge(
                    (partialResult[it] ?: ColumnSummary.empty),
                    builders[it]!!.build())
            }.toMap()
            handle.update(ExecutionSuccess(
                ExecutionValue.of(TableSummary(combinedResults).toCollection()),
                ExecutionValue.of(nextProgress.toCollection())))
        }

        return Pair(
            builders.mapValues { it.value.build() },
            nextProgress)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun lookupSummary(
        runSignature: ProcessRunSignature
    ): ExecutionResult {
        val notLoaded = mutableListOf<Path>()

        val builder = mutableMapOf<String, ColumnSummary>()

        next_input_path@
        for (inputPath in runSignature.inputs) {
            val indexDir = FilterIndex.inputIndexPath(inputPath)
            if (! Files.isDirectory(indexDir)) {
                notLoaded.add(inputPath)
                continue
            }

            for (columnName in runSignature.columnNames) {
                val columnDirName = AutoJvmUtils.sanitizeFilename(columnName)
                val columnDir = indexDir.resolve(columnDirName)

                if (! Files.exists(columnDir)) {
                    notLoaded.add(inputPath)
                    continue@next_input_path
                }

                val columnSummary = loadValueSummary(columnDir)
                val cumulative = builder.getOrDefault(columnName, ColumnSummary.empty)
                builder[columnName] = ValueSummaryBuilder.merge(cumulative, columnSummary)
            }
        }

        return ExecutionSuccess(
            ExecutionValue.of(TableSummary(builder).toCollection()),
            ExecutionValue.of(TaskProgress.ofNotStarted(
                notLoaded.map { it.fileName.toString() }
            ).toCollection()))
    }


    private suspend fun loadValueSummary(columnDir: Path): ColumnSummary {
        val count = loadSummary(columnDir)

        val nominalValueSummary = loadNominal(columnDir)
        val numericValueSummary = loadNumeric(columnDir)
        val opaqueValueSummary = loadOpaque(columnDir)

        return ColumnSummary(
            count,
            nominalValueSummary,
            numericValueSummary,
            opaqueValueSummary)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun saveValueSummary(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        saveSummary(valueSummary, columnDir)

        saveNominal(valueSummary, columnDir)
        saveNumeric(valueSummary, columnDir)
        saveOpaque(valueSummary, columnDir)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun saveSummary(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        val summaryFile = columnDir.resolve(summaryCsvFilename)

        val summaryFileContent = "Measure,Value\n" +
                "count," + valueSummary.count

        withContext(Dispatchers.IO) {
            Files.createDirectories(columnDir)
            Files.writeString(summaryFile, summaryFileContent)
        }
    }


    private suspend fun loadSummary(
        columnDir: Path
    ): Long {
        val summaryFile = columnDir.resolve(summaryCsvFilename)

        val summaryFileContent = withContext(Dispatchers.IO) {
            Files.readString(summaryFile)
        }

        @Suppress("UnnecessaryVariable")
        val count = summaryFileContent
            .split("\n")
            .last()
            .split(",")
            .last()
            .toLong()

        return count
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun saveNominal(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        if (valueSummary.nominalValueSummary.isEmpty()) {
            return
        }

        val nominalFile = columnDir.resolve(nominalCsvFilename)
        val nominalFileCsv = valueSummary.nominalValueSummary.toCsv()
        val nominalFileContent = FilterIndex.toCsv(nominalFileCsv)

        withContext(Dispatchers.IO) {
            Files.writeString(nominalFile, nominalFileContent)
        }
    }


    private suspend fun loadNominal(
        columnDir: Path
    ): NominalValueSummary {
        val nominalFile = columnDir.resolve(nominalCsvFilename)

        if (! Files.exists(nominalFile)) {
            return NominalValueSummary.empty
        }

        val nominalFileContent = withContext(Dispatchers.IO) {
            Files.readString(nominalFile)
        }

        val nominalFileCsv = FilterIndex.fromCsv(nominalFileContent)
        return NominalValueSummary.fromCsv(nominalFileCsv)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun saveNumeric(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        if (valueSummary.numericValueSummary.isEmpty()) {
            return
        }

        val numericFile = columnDir.resolve(numericCsvFilename)
        val numericFileCsv = valueSummary.numericValueSummary.toCsv()
        val numericFileContent = FilterIndex.toCsv(numericFileCsv)

        withContext(Dispatchers.IO) {
            Files.writeString(numericFile, numericFileContent)
        }
    }


    private suspend fun loadNumeric(
        columnDir: Path
    ): StatisticValueSummary {
        val numericFile = columnDir.resolve(numericCsvFilename)

        if (! Files.exists(numericFile)) {
//            return NumericValueSummary.empty
            return StatisticValueSummary.empty
        }

        val numericFileContent = withContext(Dispatchers.IO) {
            Files.readString(numericFile)
        }

        val numericFileCsv = FilterIndex.fromCsv(numericFileContent)
        return StatisticValueSummary.fromCsv(numericFileCsv)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun saveOpaque(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        if (valueSummary.opaqueValueSummary.isEmpty()) {
            return
        }

        val opaqueFile = columnDir.resolve(opaqueCsvFilename)
        val opaqueFileCsv = valueSummary.opaqueValueSummary.toCsv()
        val opaqueFileContent = FilterIndex.toCsv(opaqueFileCsv)

        withContext(Dispatchers.IO) {
            Files.writeString(opaqueFile, opaqueFileContent)
        }
    }


    private suspend fun loadOpaque(
        columnDir: Path
    ): OpaqueValueSummary {
        val opaqueFile = columnDir.resolve(opaqueCsvFilename)

        if (! Files.exists(opaqueFile)) {
            return OpaqueValueSummary.empty
        }

        val opaqueFileContent = withContext(Dispatchers.IO) {
            Files.readString(opaqueFile)
        }

        val opaqueFileCsv = FilterIndex.fromCsv(opaqueFileContent)
        return OpaqueValueSummary.fromCsv(opaqueFileCsv)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun formatCount(count: Long): String {
        return String.format("%,d", count)
    }
}