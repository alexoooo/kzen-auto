package tech.kzen.auto.server.objects.process.pivot

import tech.kzen.auto.common.objects.document.process.OutputPreview
import tech.kzen.auto.common.objects.document.process.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.process.PivotValueType
import tech.kzen.auto.server.objects.process.model.RecordItem
import tech.kzen.auto.server.objects.process.pivot.row.RowIndex
import tech.kzen.auto.server.objects.process.pivot.stats.ValueStatistics


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
    private val valueColumns = values.toList()
//    private val buffer = DoubleArray(pivotSpec.valueColumnCount())
    private val valueBuffer = DoubleArray(valueColumns.size)


    //----------------------------------------------------------
//    private var viewing = false


    //-----------------------------------------------------------------------------------------------------------------
    fun add(recordItem: RecordItem) {
//        check(! viewing) { "Can't add while viewing" }

        var present = false
        for (i in valueColumns.indices) {
            val valueColumnName = valueColumns[i]
            val valueColumnValue = recordItem.get(valueColumnName)

            if (valueColumnValue.isNullOrEmpty()) {
                valueBuffer[i] = ValueStatistics.missingValue
            }
            else {
                val asNumber = valueColumnValue.toDoubleOrNull()
                    ?.let { ValueStatistics.normalize(it) }
                    ?: Double.NaN

                valueBuffer[i] = asNumber
                present = true
            }
        }

        // NB: get row index regardless if any values present
        val rowOrdinal = rowIndex(recordItem)

        if (present) {
            valueStatistics.addOrUpdate(rowOrdinal, valueBuffer)
        }
    }


    private fun rowIndex(recordItem: RecordItem): Long {
        val rowValues = recordItem.getAll(rowColumns)
        return rowIndex.indexOf(rowValues)
    }


    //-----------------------------------------------------------------------------------------------------------------
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
        start: Long,
        count: Long,
        visitor: (List<String>) -> Unit
    ) {
        val exportSignature = ExportSignature.of(rowColumns, values)

        visitor.invoke(exportSignature.header)

        val adjustedStart = start.coerceAtLeast(0L)
        val adjustedEnd = (adjustedStart + count).coerceAtMost(rowIndex.size() - 1)

        for (rowNumber in adjustedStart until adjustedEnd) {
            val row = mutableListOf<String>()

            val rowValues = rowIndex.rowValues(rowNumber)
            row.addAll(rowValues.map { it ?: missingRowCellValue })

            val statisticValues = valueStatistics.get(rowNumber, exportSignature.valueTypes)
            for (value in statisticValues) {
                val asString =
                    if (ValueStatistics.isMissing(value)) {
                        missingStatisticCellValue
                    }
                    else {
                        value.toString()
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