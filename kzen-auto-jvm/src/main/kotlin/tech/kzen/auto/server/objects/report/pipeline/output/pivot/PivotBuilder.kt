package tech.kzen.auto.server.objects.report.pipeline.output.pivot

import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.spec.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.report.spec.PivotValueType
import tech.kzen.auto.server.objects.report.pipeline.calc.ColumnValueUtils
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordFieldFlyweight
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeaderIndex
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.RowIndex
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.stats.ValueStatistics


class PivotBuilder(
    rows: Set<String>,
    values: Set<String>,
    private val rowIndex: RowIndex,
    private val valueStatistics: ValueStatistics
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val missingRowCellValue = "<missing>"
        private const val missingStatisticCellValue = ""
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class ExportSignature(
        val header: List<String>,
        val valueTypes: List<IndexedValue<PivotValueType>>
    ) {
        companion object {
            fun of(rowColumns: List<String>, values: PivotValueTableSpec): ExportSignature {
                val header = mutableListOf<String>()
                val valueTypes = mutableListOf<IndexedValue<PivotValueType>>()

                for (rowColumn in rowColumns) {
                    header.add(rowColumn)
                }

                for ((index, e) in values.columns.toList().withIndex()) {
                    val valueColumn = e.first
                    val valueValueSpec = e.second
                    for (valueType in valueValueSpec.types) {
                        val valueHeader = "$valueColumn - $valueType"
                        header.add(valueHeader)
                        valueTypes.add(IndexedValue(index, valueType))
                    }
                }

                return ExportSignature(header, valueTypes)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val rowColumns = rows.toList()
    private val rowColumnIndex = RecordHeaderIndex(rowColumns)
    private val rowValueIndexBuffer = LongArray(rowColumns.size)

    private val valueColumns = values.toList()
    private val valueColumnIndex = RecordHeaderIndex(valueColumns)
    private val valueBuffer = DoubleArray(valueColumns.size)

    private val flyweight =
        RecordFieldFlyweight()

    private var maxOrdinal: Long = -1


    //----------------------------------------------------------
//    private var viewing = false


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if new row was created
     */
    fun add(recordRow: RecordRowBuffer, header: RecordHeader): Boolean {
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


    private fun rowIndex(recordRow: RecordRowBuffer, header: RecordHeader): Long {
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
    fun corruptPreview(values: PivotValueTableSpec, start: Long): OutputPreview {
        val exportSignature = ExportSignature.of(rowColumns, values)
        return OutputPreview(exportSignature.header, listOf(), start)
    }


    fun preview(values: PivotValueTableSpec, start: Long, count: Int): OutputPreview {
        var header: List<String>? = null
        val builder = mutableListOf<List<String>>()
        traverseWithHeader(values, start, count.toLong()) { row ->
            if (header == null) {
                header = row
            }
            else {
                builder.add(row)
            }
        }
        return OutputPreview(header!!, builder, start)
    }


    fun traverseWithHeader(
        values: PivotValueTableSpec,
        start: Long = 0,
        count: Long = rowCount(),
        visitor: (List<String>) -> Unit
    ) {
        val exportSignature = ExportSignature.of(rowColumns, values)

        visitor.invoke(exportSignature.header)

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

                val asString = when {
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