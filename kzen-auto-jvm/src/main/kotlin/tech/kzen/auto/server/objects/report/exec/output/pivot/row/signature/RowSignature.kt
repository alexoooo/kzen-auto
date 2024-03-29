package tech.kzen.auto.server.objects.report.exec.output.pivot.row.signature

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class RowSignature(
    val valueIndexes: LongArray
): Digestible {
    companion object {
        fun of(vararg valueIndexes: Long): RowSignature {
            return RowSignature(valueIndexes)
        }
    }


    override fun digest(sink: Digest.Sink) {
        sink.addInt(valueIndexes.size)
        for (valueIndex in valueIndexes) {
            sink.addLong(valueIndex)
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as RowSignature

        if (! valueIndexes.contentEquals(other.valueIndexes)) {
            return false
        }

        return true
    }


    override fun hashCode(): Int {
        return valueIndexes.contentHashCode()
    }
}