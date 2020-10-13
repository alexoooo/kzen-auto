package tech.kzen.auto.server.objects.process.pivot.row.signature

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class RowSignature(
    val valueIndexes: LongArray
): Digestible {
    companion object {
        fun of(vararg valueIndexes: Long): RowSignature {
            return RowSignature(valueIndexes)
        }
    }


    override fun digest(builder: Digest.Builder) {
        builder.addInt(valueIndexes.size)
        for (valueIndex in valueIndexes) {
            builder.addLong(valueIndex)
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