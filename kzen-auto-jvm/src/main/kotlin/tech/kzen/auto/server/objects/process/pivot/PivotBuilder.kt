package tech.kzen.auto.server.objects.process.pivot

import tech.kzen.auto.common.objects.document.process.PivotSpec
import tech.kzen.auto.server.objects.process.model.ListRecordItem
import tech.kzen.auto.server.objects.process.model.RecordItem
import tech.kzen.auto.server.objects.process.model.RecordStream
import tech.kzen.auto.server.objects.process.pivot.row.RowIndex
import tech.kzen.auto.server.objects.process.pivot.stats.ValueStatistics


class PivotBuilder(
    private val pivotSpec: PivotSpec,
    private val rowIndex: RowIndex,
    private val valueStatistics: ValueStatistics
) {
    companion object {
        private const val missingRowCellValue = "<missing>"
    }

    private val rowColumns = pivotSpec.rows.toList()
    private val valueColumns = pivotSpec.values.keys.toList()

    private var viewing = false


    fun add(recordItem: RecordItem) {
        check(! viewing) { "Can't add while viewing" }

        var rowIndexCache = -1L

        for (i in valueColumns.indices) {
            val valueColumnName = valueColumns[i]
            val valueColumnValue = recordItem.get(valueColumnName)

            if (valueColumnValue.isNullOrEmpty()) {
                continue
            }

            val asNumber = valueColumnValue.toDoubleOrNull()
                ?: Double.NaN

            if (rowIndexCache == -1L) {
                rowIndexCache = rowIndex(recordItem)
            }

            valueStatistics.add(rowIndexCache, i, asNumber)
        }
    }


    private fun rowIndex(recordItem: RecordItem): Long {
        val rowValues = recordItem.getAll(rowColumns)
        return rowIndex.indexOf(rowValues)
    }


    fun view(): RecordStream {
        check(! viewing) { "Already viewing" }
        viewing = true

        val headerIndex = mutableMapOf<String, Int>()
        val header = mutableListOf<String>()

        for (rowColumn in rowColumns) {
            header.add(rowColumn)
            headerIndex[rowColumn] = headerIndex.size
        }

        for ((valueColumn, valueTypes) in pivotSpec.values) {
            for (valueType in valueTypes.types) {
                val valueHeader = "$valueColumn - $valueType"
                header.add(valueHeader)
                headerIndex[valueHeader] = headerIndex.size
            }
        }

        val rowCount = rowIndex.size
        var nextRowIndex = 0L

        return object : RecordStream {
            override fun header(): List<String> {
                return header
            }

            override fun hasNext(): Boolean {
                return nextRowIndex < rowCount
            }

            override fun next(): RecordItem {
                val cells = mutableListOf<String>()

                val rowValues = rowIndex.rowValues(nextRowIndex)
                cells.addAll(rowValues.map { it ?: missingRowCellValue })

                for ((columnIndex, valueTypes) in pivotSpec.values.values.withIndex()) {
                    for (valueType in valueTypes.types) {
                        val value = valueStatistics.get(nextRowIndex, columnIndex, valueType)
                        cells.add(value.toString())
                    }
                }

                nextRowIndex++

                return ListRecordItem(
                    headerIndex, cells)
            }

            override fun close() {
                viewing = false
            }
        }
    }
}