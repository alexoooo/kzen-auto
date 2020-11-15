package tech.kzen.auto.server.objects.process.pivot.row

import tech.kzen.auto.server.objects.process.pivot.row.signature.RowSignature
import tech.kzen.auto.server.objects.process.pivot.row.signature.RowSignatureIndex
import tech.kzen.auto.server.objects.process.pivot.row.value.RowValueIndex


class RowIndex(
    private val rowValueIndex: RowValueIndex,
    private val rowSignatureIndex: RowSignatureIndex
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val missingRowValueIndex = -1L
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun size(): Long {
        return rowSignatureIndex.size()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun indexOf(rowValues: List<String?>): Long {
        val rowValueIndexList = LongArray(rowValues.size)

        for (rowValue in rowValues.withIndex()) {
            val rowValueIndex =
                if (rowValue.value != null) {
                    rowValueIndex.getOrAddIndex(rowValue.value!!)
                }
                else {
                    missingRowValueIndex
                }

            rowValueIndexList[rowValue.index] = rowValueIndex
        }

        return rowSignatureIndex.getOrAddIndex(
            RowSignature(rowValueIndexList))
    }


    fun rowValues(rowIndex: Long): List<String?> {
        val rowValueIndexList = rowSignatureIndex.getSignature(rowIndex)

        val rowValues = mutableListOf<String?>()
        for (valueIndex in rowValueIndexList.valueIndexes) {
            val rowValue =
                if (valueIndex == missingRowValueIndex) {
                    null
                }
                else {
                    rowValueIndex.getValue(valueIndex)
                }

            rowValues.add(rowValue)
        }

        return rowValues
    }


    override fun close() {
        rowValueIndex.close()
        rowSignatureIndex.close()
    }
}