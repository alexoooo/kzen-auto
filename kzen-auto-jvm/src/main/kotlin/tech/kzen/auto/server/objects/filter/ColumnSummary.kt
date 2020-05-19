package tech.kzen.auto.server.objects.filter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.reactive.NominalValueSummary
import tech.kzen.auto.common.paradigm.reactive.OpaqueValueSummary
import tech.kzen.auto.common.paradigm.reactive.StatisticValueSummary
import tech.kzen.auto.common.paradigm.reactive.ValueSummary
import tech.kzen.auto.server.objects.filter.model.ValueSummaryBuilder
import tech.kzen.auto.util.AutoJvmUtils
import java.nio.file.Files
import java.nio.file.Path


object ColumnSummary {
    //-----------------------------------------------------------------------------------------------------------------
    private val logger = LoggerFactory.getLogger(ColumnSummary::class.java)


    private const val summaryCsvFilename = "summary.csv"
    private const val nominalCsvFilename = "nominal.csv"
    private const val numericCsvFilename = "numeric.csv"
    private const val opaqueCsvFilename = "opaque.csv"


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun summarizeAll(
        inputPaths: List<Path>,
        columnName: String
    ): ValueSummary {
        var builder = ValueSummary.empty
        for (inputPath in inputPaths) {
            val valueSummary = getValueSummary(inputPath, columnName)
            builder = ValueSummaryBuilder.merge(builder, valueSummary)
        }
        return builder
    }


    suspend fun getValueSummary(
        inputPath: Path,
        columnName: String
    ): ValueSummary {
        val columnDirName = AutoJvmUtils.sanitizeFilename(columnName)
        val columnDir = FilterIndex.inputIndexPath(inputPath).resolve(columnDirName)

        return if (Files.exists(columnDir)) {
            loadValueSummary(columnDir)
        }
        else {
            // TODO: avoid re-calculation on mis-matched columns

            val valueSummaries = buildValueSummaries(inputPath)

            for (e in valueSummaries) {
                val saveColumnDirName = AutoJvmUtils.sanitizeFilename(e.key)
                val saveColumnDir = FilterIndex.inputIndexPath(inputPath).resolve(saveColumnDirName)
                saveValueSummary(e.value, saveColumnDir)
            }
            return valueSummaries[columnName]
                ?: throw IllegalArgumentException("Not found: $inputPath - $columnName")
        }
    }


    private suspend fun loadValueSummary(columnDir: Path): ValueSummary {
        val count = loadSummary(columnDir)

        val nominalValueSummary = loadNominal(columnDir)
        val numericValueSummary = loadNumeric(columnDir)
        val opaqueValueSummary = loadOpaque(columnDir)

        return ValueSummary(
            count,
            nominalValueSummary,
            numericValueSummary,
            opaqueValueSummary)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun saveValueSummary(
        valueSummary: ValueSummary,
        columnDir: Path
    ) {
        saveSummary(valueSummary, columnDir)

        saveNominal(valueSummary, columnDir)
        saveNumeric(valueSummary, columnDir)
        saveOpaque(valueSummary, columnDir)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun saveSummary(
        valueSummary: ValueSummary,
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
        valueSummary: ValueSummary,
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
        valueSummary: ValueSummary,
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
        valueSummary: ValueSummary,
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
    private suspend fun buildValueSummaries(
        inputPath: Path
    ): Map<String, ValueSummary> {
        logger.info("Summarizing columns: {}", inputPath)

        val builders = mutableMapOf<String, ValueSummaryBuilder>()

        withContext(Dispatchers.IO) {
            FileStreamer.open(inputPath)!!.use { stream ->
                for (columnName in stream.header()) {
                    builders[columnName] = ValueSummaryBuilder()
                }

                var count: Long = 0
                while (stream.hasNext()) {
                    count++
                    if (count % 250_000 == 0L) {
                        logger.info("Summarized: {}", formatCount(count))
                    }

                    val record = stream.next()
                    for (columnName in stream.header()) {
                        val value = record.get(columnName)!!
                        builders[columnName]!!.add(value)
                    }
                }

                logger.info("Finished file summary: {}", formatCount(count))
            }
        }

        return builders.mapValues { it.value.build() }
    }


    private fun formatCount(count: Long): String {
        return String.format("%,d", count)
    }
}