package tech.kzen.auto.server.objects.report.exec.summary

import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabelMap
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.summary.*
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvReportDefiner
import tech.kzen.auto.server.objects.report.exec.summary.model.ValueSummaryBuilder
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture


class ReportSummary(
    initialReportRunContext: ReportRunContext,
    runDir: Path
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("ConstPropertyName")
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
            return CsvReportDefiner
                .literal(csv)
                .map { it.toList() }
        }


        private fun columnDir(summaryDir: Path, headerLabel: HeaderLabel): Path {
            val columnDirName = FormatUtils.sanitizeFilename(headerLabel.render())
            return summaryDir.resolve(columnDirName)
        }


        //-------------------------------------------------------------------------------------------------------------
        fun tableSummaryOffline(
            reportRunContext: ReportRunContext
        ): TableSummary? {
            val summaryDir = reportRunContext.runDir.resolve(summaryDirName)
            if (! Files.exists(summaryDir)) {
                return null
            }

            val builder = mutableMapOf<HeaderLabel, ColumnSummary>()

            for (columnName in reportRunContext.inputAndFormulaColumns.values) {
                val columnDir = columnDir(summaryDir, columnName)

                if (! Files.exists(columnDir)) {
                    continue
                }

                val columnSummary = loadValueSummary(columnDir)
                val cumulative = builder.getOrDefault(columnName, ColumnSummary.empty)
                builder[columnName] = ValueSummaryBuilder.merge(cumulative, columnSummary)
            }

            return TableSummary(HeaderLabelMap(builder))
        }


        private  fun loadValueSummary(columnDir: Path): ColumnSummary {
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


        private fun loadSummary(
            columnDir: Path
        ): Long {
            val summaryFile = columnDir.resolve(summaryCsvFilename)
            val summaryFileContent = Files.readString(summaryFile)

            @Suppress("UnnecessaryVariable")
            val count = summaryFileContent
                .split("\n")
                .last()
                .split(",")
                .last()
                .toLong()

            return count
        }


        private fun loadNominal(
            columnDir: Path
        ): NominalValueSummary {
            val nominalFile = columnDir.resolve(nominalCsvFilename)
            if (! Files.exists(nominalFile)) {
                return NominalValueSummary.empty
            }

            val nominalFileContent = Files.readString(nominalFile)
            val nominalFileCsv = fromCsv(nominalFileContent)
            return NominalValueSummary.fromCsv(nominalFileCsv)
        }


        private fun loadNumeric(
            columnDir: Path
        ): StatisticValueSummary {
            val numericFile = columnDir.resolve(numericCsvFilename)
            if (! Files.exists(numericFile)) {
                return StatisticValueSummary.empty
            }

            val numericFileContent = Files.readString(numericFile)
            val numericFileCsv = fromCsv(numericFileContent)
            return StatisticValueSummary.ofCsv(numericFileCsv)
        }


        private fun loadOpaque(
            columnDir: Path
        ): OpaqueValueSummary {
            val opaqueFile = columnDir.resolve(opaqueCsvFilename)
            if (! Files.exists(opaqueFile)) {
                return OpaqueValueSummary.empty
            }

            val opaqueFileContent = Files.readString(opaqueFile)
            val opaqueFileCsv = fromCsv(opaqueFileContent)
            return OpaqueValueSummary.fromCsv(opaqueFileCsv)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val summaryDir = runDir.resolve(summaryDirName)


    @Volatile
    private var viewResponse: CompletableFuture<TableSummary>? = null

    @Volatile
    private var closed: Boolean = false


    private val headerIndex = RecordHeaderIndex(
        initialReportRunContext.inputAndFormulaColumns)

    private val builders: List<ValueSummaryBuilder> =
        headerIndex.columnHeaders.values.map { ValueSummaryBuilder() }


    private val flyweight =
        FlatFileRecordField()


    //-----------------------------------------------------------------------------------------------------------------
    private fun save() {
        for (i in headerIndex.columnHeaders.values.indices) {
            val columnName = headerIndex.columnHeaders.values[i]
            val columnDir = columnDir(summaryDir, columnName)

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


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun previewFromOtherThread(): TableSummary? {
        check(viewResponse == null)

        if (closed) {
            return null
        }

        val response = CompletableFuture<TableSummary>()
        viewResponse = response

        val value = response.get()

        if (value.isEmpty()) {
            return null
        }
        return value
    }


    fun handleViewRequest() {
        val response = viewResponse
            ?: return

        val tableSummary = TableSummary(HeaderLabelMap(
            headerIndex
                .columnHeaders
                .values
                .withIndex()
                .associate { it.value to builders[it.index].build() }))

        response.complete(tableSummary)
        viewResponse = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun add(recordRow: FlatFileRecord, header: HeaderListing) {
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
//    @Synchronized
    override fun close() {
        closed = true
        viewResponse?.complete(TableSummary.empty)
        save()
    }
}