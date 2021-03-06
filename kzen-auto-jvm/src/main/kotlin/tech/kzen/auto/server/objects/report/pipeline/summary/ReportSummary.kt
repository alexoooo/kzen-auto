package tech.kzen.auto.server.objects.report.pipeline.summary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tech.kzen.auto.common.objects.document.report.summary.*
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.pipeline.input.parse.csv.CsvProcessorDefiner
import tech.kzen.auto.server.objects.report.pipeline.summary.model.ValueSummaryBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class ReportSummary(
    initialReportRunContext: ReportRunContext,
    runDir: Path,
    private val taskHandle: TaskHandle?
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        //-------------------------------------------------------------------------------------------------------------
        const val summaryDirName = "summary"

        private const val summaryCsvFilename = "summary.csv"
        private const val nominalCsvFilename = "nominal.csv"
        private const val numericCsvFilename = "numeric.csv"
        private const val opaqueCsvFilename = "opaque.csv"


        //-------------------------------------------------------------------------------------------------------------
        private fun toCsv(csv: List<List<String>>): String {
            return csv.joinToString("\n") { FlatFileRecord.of(it).toCsv() }
        }


        private fun fromCsv(csv: String): List<List<String>> {
            return CsvProcessorDefiner
                .literal(csv)
                .map { it.toList() }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val summaryDir = runDir.resolve(summaryDirName)


    @Volatile
    private var viewRequested: Boolean = false

    @Volatile
    private var viewResponse = CompletableFuture<TableSummary>()


    private val headerIndex = RecordHeaderIndex(
        initialReportRunContext.inputAndFormulaColumns)

    private val builders: List<ValueSummaryBuilder> =
        headerIndex.columnHeaders.values.map { ValueSummaryBuilder() }


    private val flyweight =
        FlatFileRecordField()


    //-----------------------------------------------------------------------------------------------------------------
    private fun columnDir(columnName: String): Path {
        val columnDirName = FormatUtils.sanitizeFilename(columnName)
        return summaryDir.resolve(columnDirName)
    }


    private suspend fun load(): TableSummary? {
        if (! Files.exists(summaryDir)) {
            return null
        }

        val builder = mutableMapOf<String, ColumnSummary>()

        for (columnName in headerIndex.columnHeaders.values) {
            val columnDir = columnDir(columnName)

            if (! Files.exists(columnDir)) {
                continue
            }

            val columnSummary = loadValueSummary(columnDir)
            val cumulative = builder.getOrDefault(columnName, ColumnSummary.empty)
            builder[columnName] = ValueSummaryBuilder.merge(cumulative, columnSummary)
        }

        return TableSummary(builder)
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


    private fun save() {
        for (i in headerIndex.columnHeaders.values.indices) {
            val columnName = headerIndex.columnHeaders.values[i]
            val columnDir = columnDir(columnName)

            val columnBuilder = builders[i]
            val columnSummary = columnBuilder.build()

            saveValueSummary(columnSummary, columnDir)
        }
    }


    private fun saveValueSummary(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        saveSummary(valueSummary, columnDir)

        saveNominal(valueSummary, columnDir)
        saveNumeric(valueSummary, columnDir)
        saveOpaque(valueSummary, columnDir)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun saveNominal(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        if (valueSummary.nominalValueSummary.isEmpty()) {
            return
        }

        val nominalFile = columnDir.resolve(nominalCsvFilename)
        val nominalFileCsv = valueSummary.nominalValueSummary.toCsv()
        val nominalFileContent = toCsv(nominalFileCsv)

        Files.writeString(nominalFile, nominalFileContent)
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

        val nominalFileCsv = fromCsv(nominalFileContent)
        return NominalValueSummary.fromCsv(nominalFileCsv)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun saveNumeric(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        if (valueSummary.numericValueSummary.isEmpty()) {
            return
        }

        val numericFile = columnDir.resolve(numericCsvFilename)
        val numericFileCsv = valueSummary.numericValueSummary.asCsv()
        val numericFileContent = toCsv(numericFileCsv)

        Files.writeString(numericFile, numericFileContent)
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

        val numericFileCsv = fromCsv(numericFileContent)
        return StatisticValueSummary.ofCsv(numericFileCsv)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun saveSummary(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        val summaryFile = columnDir.resolve(summaryCsvFilename)

        val summaryFileContent = "Measure,Value\n" +
                "count," + valueSummary.count

        Files.createDirectories(columnDir)
        Files.writeString(summaryFile, summaryFileContent)
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
    private fun saveOpaque(
        valueSummary: ColumnSummary,
        columnDir: Path
    ) {
        if (valueSummary.opaqueValueSummary.isEmpty()) {
            return
        }

        val opaqueFile = columnDir.resolve(opaqueCsvFilename)
        val opaqueFileCsv = valueSummary.opaqueValueSummary.toCsv()
        val opaqueFileContent = toCsv(opaqueFileCsv)

        Files.writeString(opaqueFile, opaqueFileContent)
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

        val opaqueFileCsv = fromCsv(opaqueFileContent)
        return OpaqueValueSummary.fromCsv(opaqueFileCsv)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun view(): TableSummary {
        if (taskHandle == null) {
            return viewInCurrentThread()
        }

        viewRequested = true

        var response: TableSummary? = null
        while (response == null) {
            response =
                try {
                    viewResponse.get(1, TimeUnit.SECONDS)
                }
                catch (e: TimeoutException) {
                    if (taskHandle.isTerminated()) {
                        viewInCurrentThread()
                    }
                    else {
                        continue
                    }
                }
        }

        viewResponse = CompletableFuture()

        return response
    }


    private fun viewInCurrentThread(): TableSummary {
        return runBlocking {
            load() ?: TableSummary.empty
        }
    }


    fun handleViewRequest() {
        if (! viewRequested) {
            return
        }

        val tableSummary = TableSummary(
            headerIndex
                .columnHeaders
                .values
                .withIndex()
                .associate { it.value to builders[it.index].build() })

        viewResponse.complete(tableSummary)
        viewRequested = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun add(recordRow: FlatFileRecord, header: RecordHeader) {
        flyweight.selectHost(recordRow);

        val indices = headerIndex.indices(header)
        for (i in builders.indices) {
            val itemIndex = indices[i]
            if (itemIndex == -1) {
                continue
            }

            flyweight.selectField(itemIndex)
            builders[i].add(flyweight)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        if (taskHandle != null) {
            save()
        }
    }
}