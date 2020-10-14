package tech.kzen.auto.server.objects.process.pivot

import tech.kzen.auto.common.objects.document.process.PivotSpec
import tech.kzen.auto.common.objects.document.process.PivotValueType
import tech.kzen.auto.server.objects.process.model.ListRecordItem
import tech.kzen.auto.server.objects.process.model.RecordItem
import tech.kzen.auto.server.objects.process.model.RecordStream
import tech.kzen.auto.server.objects.process.pivot.row.RowIndex
import tech.kzen.auto.server.objects.process.pivot.stats.ValueStatistics


class PivotBuilder(
    private val pivotSpec: PivotSpec,
    private val rowIndex: RowIndex,
    private val valueStatistics: ValueStatistics
):
    AutoCloseable
{
    companion object {
        private const val missingRowCellValue = "<missing>"
    }

    private val rowColumns = pivotSpec.rows.toList()
    private val valueColumns = pivotSpec.values.keys.toList()
    private val buffer = DoubleArray(pivotSpec.valueColumnCount())

    private val header: List<String>
    private val headerIndex: Map<String, Int>
    private val columnTypes: List<IndexedValue<PivotValueType>>

    init {
        val headerBuffer = mutableListOf<String>()
        val headerIndexBuffer = mutableMapOf<String, Int>()
        val columnTypesBuffer = mutableListOf<IndexedValue<PivotValueType>>()

        for (rowColumn in rowColumns) {
            headerBuffer.add(rowColumn)
            headerIndexBuffer[rowColumn] = headerIndexBuffer.size
        }

        for ((index, e) in pivotSpec.values.toList().withIndex()) {
            val valueColumn = e.first
            val valueTypes = e.second
            for (valueType in valueTypes.types) {
                val valueHeader = "$valueColumn - $valueType"
                headerBuffer.add(valueHeader)
                headerIndexBuffer[valueHeader] = headerIndexBuffer.size
                columnTypesBuffer.add(IndexedValue(index, valueType))
            }
        }

        header = headerBuffer
        headerIndex = headerIndexBuffer
        columnTypes = columnTypesBuffer
    }

    private var viewing = false


    fun add(recordItem: RecordItem) {
        check(! viewing) { "Can't add while viewing" }

//        var rowIndexCache = -1L
        var present = false
        for (i in valueColumns.indices) {
            val valueColumnName = valueColumns[i]
            val valueColumnValue = recordItem.get(valueColumnName)

            if (valueColumnValue.isNullOrEmpty()) {
                buffer[i] = ValueStatistics.missingValue
                continue
            }
            else {
                present = true
            }

            val asNumber = valueColumnValue.toDoubleOrNull()
                ?.let { ValueStatistics.normalize(it) }
                ?: Double.NaN

            buffer[i] = asNumber
//            if (rowIndexCache == -1L) {
//                rowIndexCache = rowIndex(recordItem)
//            }

//            valueStatistics.add(rowIndexCache, i, asNumber)
        }

        if (present) {
            val rowOrdinal = rowIndex(recordItem)
            valueStatistics.add(rowOrdinal, buffer)
        }
    }


    private fun rowIndex(recordItem: RecordItem): Long {
        val rowValues = recordItem.getAll(rowColumns)
        return rowIndex.indexOf(rowValues)
    }


    fun view(): RecordStream {
        check(! viewing) { "Already viewing" }
        viewing = true

        val header = mutableListOf<String>()
        val headerIndex = mutableMapOf<String, Int>()

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

                val values = valueStatistics.get(nextRowIndex, columnTypes)
                for (value in values) {
                    cells.add(value.toString())
                }

//                for ((columnIndex, valueTypes) in pivotSpec.values.values.withIndex()) {
//                    for (valueType in valueTypes.types) {
//                        val value = valueStatistics.get(nextRowIndex, columnIndex, valueType)
//                        cells.add(value.toString())
//                    }
//                }

                nextRowIndex++

                return ListRecordItem(
                    headerIndex, cells)
            }

            override fun close() {
                viewing = false
            }
        }
    }


    override fun close() {
        rowIndex.close()
        valueStatistics.close()
    }
}