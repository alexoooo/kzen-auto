package tech.kzen.auto.server.objects.process.pivot

import tech.kzen.auto.common.objects.document.process.PivotValueSpec
import tech.kzen.auto.common.objects.document.process.PivotValueType
import tech.kzen.auto.server.objects.process.model.ListRecordItem
import tech.kzen.auto.server.objects.process.model.RecordItem
import tech.kzen.auto.server.objects.process.pivot.row.RowIndex
import tech.kzen.auto.server.objects.process.pivot.stats.ValueStatistics
import tech.kzen.auto.server.objects.process.stream.RecordStream


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
    private val rowColumns = rows.toList()
    private val valueColumns = values.toList()
//    private val buffer = DoubleArray(pivotSpec.valueColumnCount())
    private val valueBuffer = DoubleArray(valueColumns.size)


    //----------------------------------------------------------
    private var viewing = false


    //-----------------------------------------------------------------------------------------------------------------
    fun add(recordItem: RecordItem) {
        check(! viewing) { "Can't add while viewing" }

        var present = false
        for (i in valueColumns.indices) {
            val valueColumnName = valueColumns[i]
            val valueColumnValue = recordItem.get(valueColumnName)

            if (valueColumnValue.isNullOrEmpty()) {
                valueBuffer[i] = ValueStatistics.missingValue
                continue
            }
            else {
                present = true
            }

            val asNumber = valueColumnValue.toDoubleOrNull()
                ?.let { ValueStatistics.normalize(it) }
                ?: Double.NaN

            valueBuffer[i] = asNumber
        }

        // NB: get row index
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
//    fun view(): RecordStream {
//        return view()
//    }


    fun view(values: Map<String, PivotValueSpec>): RecordStream {
        check(! viewing) { "Already viewing" }
        viewing = true

        val header: List<String>
        val headerIndex: Map<String, Int>
        val valueTypes: List<IndexedValue<PivotValueType>>

        val headerBuffer = mutableListOf<String>()
        val headerIndexBuffer = mutableMapOf<String, Int>()
        val valueTypesBuffer = mutableListOf<IndexedValue<PivotValueType>>()

        for (rowColumn in rowColumns) {
            headerBuffer.add(rowColumn)
            headerIndexBuffer[rowColumn] = headerIndexBuffer.size
        }

        for ((index, e) in values.toList().withIndex()) {
            val valueColumn = e.first
            val valueValueSpec = e.second
            for (valueType in valueValueSpec.types) {
                val valueHeader = "$valueColumn - $valueType"
                headerBuffer.add(valueHeader)
                headerIndexBuffer[valueHeader] = headerIndexBuffer.size
                valueTypesBuffer.add(IndexedValue(index, valueType))
            }
        }

        header = headerBuffer
        headerIndex = headerIndexBuffer
        valueTypes = valueTypesBuffer

        val rowCount = rowIndex.size
        var nextRowIndex = 0L

        return object: RecordStream {
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

                val statisticValues = valueStatistics.get(nextRowIndex, valueTypes)

                for (value in statisticValues) {
                    val asString =
                        if (ValueStatistics.isMissing(value)) {
                            missingStatisticCellValue
                        }
                        else {
                            value.toString()
                        }

                    cells.add(asString)
                }

                nextRowIndex++

                return ListRecordItem(
                    header, headerIndex, cells)
            }

            override fun close() {
                viewing = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        rowIndex.close()
        valueStatistics.close()
    }
}