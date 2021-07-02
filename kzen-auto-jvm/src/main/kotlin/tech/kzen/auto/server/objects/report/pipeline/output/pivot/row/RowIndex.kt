package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row

import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest.DigestOrdinal
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.signature.RowSignatureIndex
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value.RowValueIndex


class RowIndex(
    private val rowValueIndex: RowValueIndex,
    private val rowSignatureIndex: RowSignatureIndex
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        const val missingRowValueIndex = -1L
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private val singletonSignature =
        rowSignatureIndex.signatureSize() == 1


    //-----------------------------------------------------------------------------------------------------------------
    fun size(): Long {
        if (singletonSignature) {
            return rowValueIndex.size()
        }

        return rowSignatureIndex.size()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun valueIndexOfMissing(): DigestOrdinal {
        return rowValueIndex.getOrAddIndexMissing()
    }


    fun valueIndexOf(value: FlatFileRecordField): DigestOrdinal {
        return rowValueIndex.getOrAddIndex(value)
    }


    fun getOrAdd(valueIndexes: LongArray): Long {
        if (singletonSignature) {
            return valueIndexes[0]
        }

        return rowSignatureIndex.getOrAddIndex(valueIndexes)
    }


    fun add(valueIndexes: LongArray): Long {
        if (singletonSignature) {
            return valueIndexes[0]
        }

        return rowSignatureIndex.addIndex(valueIndexes)
    }


    fun getOrAdd(rowValues: List<String?>): Long {
        val rowValueIndexList = LongArray(rowValues.size)

        for (rowValue in rowValues.withIndex()) {
            val rowValueIndex =
                if (rowValue.value != null) {
                    rowValueIndex.getOrAddIndex(rowValue.value!!)
                }
                else {
                    rowValueIndex.getOrAddIndexMissing()
                }

            rowValueIndexList[rowValue.index] = rowValueIndex.ordinal()
        }

        return getOrAdd(rowValueIndexList)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rowValues(rowIndex: Long): List<String?> {
        if (singletonSignature) {
            return listOf(rowValueIndex.getValue(rowIndex))
        }

        val rowValueIndexList = rowSignatureIndex.getSignature(rowIndex)

        val rowValues = mutableListOf<String?>()
        for (valueIndex in rowValueIndexList.valueIndexes) {
            val rowValue = rowValueIndex.getValue(valueIndex)
            rowValues.add(rowValue)
        }

        return rowValues
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        rowValueIndex.close()
        rowSignatureIndex.close()
    }
}