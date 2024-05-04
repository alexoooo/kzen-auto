package tech.kzen.auto.server.objects.report.exec.output.pivot

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputPivotExportSignature
import tech.kzen.auto.common.objects.document.report.output.OutputPivotHeaderLabel
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.report.exec.calc.ColumnValueUtils
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.exec.output.pivot.row.RowIndex
import tech.kzen.auto.server.objects.report.exec.output.pivot.row.digest.H2DigestIndex
import tech.kzen.auto.server.objects.report.exec.output.pivot.row.signature.StoreRowSignatureIndex
import tech.kzen.auto.server.objects.report.exec.output.pivot.row.signature.store.BufferedIndexedSignatureStore
import tech.kzen.auto.server.objects.report.exec.output.pivot.row.signature.store.FileIndexedSignatureStore
import tech.kzen.auto.server.objects.report.exec.output.pivot.row.value.StoreRowValueIndex
import tech.kzen.auto.server.objects.report.exec.output.pivot.row.value.store.BufferedIndexedTextStore
import tech.kzen.auto.server.objects.report.exec.output.pivot.row.value.store.FileIndexedTextStore
import tech.kzen.auto.server.objects.report.exec.output.pivot.stats.BufferedValueStatistics
import tech.kzen.auto.server.objects.report.exec.output.pivot.stats.ValueStatistics
import tech.kzen.auto.server.objects.report.exec.output.pivot.stats.store.FileValueStatisticsStore
import tech.kzen.auto.server.objects.report.exec.output.pivot.store.BufferedOffsetStore
import tech.kzen.auto.server.objects.report.exec.output.pivot.store.FileOffsetStore
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path


class PivotBuilder(
    private val rows: HeaderListing,
    private val values: HeaderListing,
    private val rowIndex: RowIndex,
    private val valueStatistics: ValueStatistics
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("ConstPropertyName")
    companion object {
        private const val missingRowCellValue = "<missing>"
        private const val missingStatisticCellValue = ""


        fun downloadCsvOffline(reportRunContext: ReportRunContext): InputStream {
            val pivotSpec = reportRunContext.analysis.pivot
            val pivotBuilder = create(
                pivotSpec.rows,
                HeaderListing(pivotSpec.values.columns.keys.toList()),
                reportRunContext.runDir)

            val input = PipedInputStream()
            val output = PipedOutputStream(input)
            val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)

            Thread {
                pivotBuilder.use { pivotBuilder ->
                    val exportSignature = OutputPivotExportSignature.of(
                        pivotSpec.rows, reportRunContext.analysis.pivot.values)
                    val valueTypes = exportSignature.valueTypes

                    val headerValues = exportSignature.header
                    val flatFileRecord = FlatFileRecord(
                        headerValues.sumOf { it.headerLabel.text.length },
                        headerValues.size)
                    flatFileRecord.addAll(headerValues.map { it.headerLabel.render() })
                    flatFileRecord.writeCsv(writer)

                    val rowIndex = pivotBuilder.rowIndex
                    val size = rowIndex.size()
                    val valueStatistics = pivotBuilder.valueStatistics
                    val valueBuffer = ArrayList<String?>()

                    for (rowNumber in 0 until size) {
                        writer.write(tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvFormatUtils.lineFeedInt)

                        flatFileRecord.clear()
                        valueBuffer.clear()

                        rowIndex.rowValuesInto(rowNumber, valueBuffer)

                        for (i in 0 until valueBuffer.size) {
                            flatFileRecord.add(valueBuffer[i] ?: missingRowCellValue)
                        }

                        val statisticValues = valueStatistics.get(rowNumber, valueTypes)
                        @Suppress("ReplaceManualRangeWithIndicesCalls")
                        for (i in 0 until valueTypes.size) {
                            val value = statisticValues[i]
                            val type = valueTypes[i].value

                            val asString = formatStatistic(value, type)
                            flatFileRecord.add(asString)
                        }

                        flatFileRecord.writeCsv(writer)
                    }

                    writer.flush()
                    output.close()
                }
            }.start()

            return input
        }


        fun create(
            rows: HeaderListing,
            values: HeaderListing,
            pivotDir: Path
        ): PivotBuilder {
            val rowTextContentFile = pivotDir.resolve("row-text-value.bin")
            val rowTextIndexFile= pivotDir.resolve("row-text-index.bin")
            val rowSignatureFile = pivotDir.resolve("row-signature.bin")
            val valueStatisticsFile = pivotDir.resolve("value-statistics.bin")
            val rowValueDigestDir = pivotDir.resolve("row-text-digest")
            val rowSignatureDigestDir = pivotDir.resolve("row-signature-digest")

            val rowValueDigestIndex = H2DigestIndex(rowValueDigestDir)

            val textOffsetStore = BufferedOffsetStore(
                FileOffsetStore(rowTextIndexFile))

            val indexedTextStore = BufferedIndexedTextStore(
                FileIndexedTextStore(rowTextContentFile, textOffsetStore))

            val rowValueIndex = StoreRowValueIndex(
                rowValueDigestIndex, indexedTextStore)

            val rowSignatureDigestIndex =
                H2DigestIndex(rowSignatureDigestDir)

            val indexedSignatureStore = BufferedIndexedSignatureStore(
                FileIndexedSignatureStore(rowSignatureFile, rows.values.size))

            val rowSignatureIndex = StoreRowSignatureIndex(
                rowSignatureDigestIndex, indexedSignatureStore)

            val valueStatistics = BufferedValueStatistics(
                FileValueStatisticsStore(valueStatisticsFile, values.values.size))

            return PivotBuilder(
                rows,
                values,
                RowIndex(rowValueIndex, rowSignatureIndex),
                valueStatistics)
        }


        private fun formatStatistic(value: Double, type: PivotValueType): String {
            return when {
                ValueStatistics.isMissing(value) -> {
                    missingStatisticCellValue
                }

                type == PivotValueType.Count -> {
                    value.toLong().toString()
                }

                else -> {
                    ColumnValueUtils.formatDecimal(value)
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val rowColumnIndex = RecordHeaderIndex(rows)
    private val rowValueIndexBuffer = LongArray(rows.values.size)

    private val valueColumnIndex = RecordHeaderIndex(values)
    private val valueBuffer = DoubleArray(values.values.size)

    private val flyweight =
        FlatFileRecordField()

    private var maxOrdinal: Long = -1


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if new row was created
     */
    fun add(recordRow: FlatFileRecord, header: HeaderListing): Boolean {
        val headerIndexes = valueColumnIndex.indices(header)

        var present = false

        for (i in valueBuffer.indices) {
            val headerIndex = headerIndexes[i]

            valueBuffer[i] =
                if (headerIndex == -1) {
                    ValueStatistics.missingValue
                }
                else {
                    present = true
                    flyweight.selectHostField(recordRow, headerIndex)
                    flyweight.toDoubleOrNan()
                }
        }

        // NB: get row index regardless if any values present
        val rowOrdinal = rowIndex(recordRow, header)

        if (present) {
            valueStatistics.addOrUpdate(rowOrdinal, valueBuffer)
        }

        if (maxOrdinal < rowOrdinal) {
            maxOrdinal = rowOrdinal
            return true
        }
        return false
    }


    private fun rowIndex(recordRow: FlatFileRecord, header: HeaderListing): Long {
        var valueAdded = false
        val headerIndices = rowColumnIndex.indices(header)

        for (i in headerIndices.indices) {
            val index = headerIndices[i]

            val rowValueIndex =
                if (index == -1) {
                    rowIndex.valueIndexOfMissing()
                }
                else {
                    flyweight.selectHostField(recordRow, index)

                    val rowOrdinal = rowIndex.valueIndexOf(flyweight)
                    if (rowOrdinal.wasAdded()) {
                        valueAdded = true
                    }
                    rowOrdinal
                }

            rowValueIndexBuffer[i] = rowValueIndex.ordinal()
        }

        return when {
            valueAdded ->
                rowIndex.add(rowValueIndexBuffer)

            else ->
                rowIndex.getOrAdd(rowValueIndexBuffer)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun corruptPreview(values: PivotValueTableSpec, start: Long): OutputPreview {
//        val exportSignature = OutputPivotExportSignature.of(rows, values)
//        return OutputPreview(exportSignature.header, listOf(), start)
//    }


    fun preview(values: PivotValueTableSpec, start: Long, count: Int): OutputPreview {
        val exportSignature = OutputPivotExportSignature.of(rows, values)
        val headerListing = exportSignature.header.map { it.render() }

        val builder = mutableListOf<List<String>>()
        traverse(values, start, count.toLong()) { row ->
            builder.add(row)
        }
        return OutputPreview(headerListing, builder, start)
    }


    private fun traverse(
        values: PivotValueTableSpec,
        start: Long = 0,
        count: Long = rowCount(),
        visitor: (List<String>) -> Unit
    ) {
        val exportSignature = OutputPivotExportSignature.of(rows, values)

        val adjustedStart = start.coerceAtLeast(0L)
        val adjustedEndExclusive = (adjustedStart + count).coerceAtMost(rowIndex.size())

        for (rowNumber in adjustedStart until adjustedEndExclusive) {
            val rowValues = rowIndex.rowValues(rowNumber)

            val columnCount = rowValues.size + exportSignature.valueTypes.size
            val row = ArrayList<String>(columnCount)

            row.addAll(rowValues.map { it ?: missingRowCellValue })

            val statisticValues = valueStatistics.get(rowNumber, exportSignature.valueTypes)
            for (i in exportSignature.valueTypes.indices) {
                val value = statisticValues[i]
                val type = exportSignature.valueTypes[i].value

                val asString = formatStatistic(value, type)
                row.add(asString)
            }

            visitor.invoke(row)
        }
    }


    fun rowCount(): Long {
        return rowIndex.size()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        rowIndex.close()
        valueStatistics.close()
    }
}