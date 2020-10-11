package tech.kzen.auto.server.objects.process.pivot.row.signature


data class RowSignature(
    val valueIndexes: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as RowSignature

        if (!valueIndexes.contentEquals(other.valueIndexes)) {
            return false
        }

        return true
    }


    override fun hashCode(): Int {
        return valueIndexes.contentHashCode()
    }
}