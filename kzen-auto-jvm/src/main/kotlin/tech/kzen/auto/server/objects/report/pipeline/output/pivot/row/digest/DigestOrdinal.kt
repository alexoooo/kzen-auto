package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest


@JvmInline
value class DigestOrdinal(
//inline class DigestOrdinal(
    private val value: Long
) {
    companion object {
        fun ofExisting(ordinal: Long): DigestOrdinal {
            check(ordinal >= 0)
            return DigestOrdinal(ordinal)
        }

        fun ofAdded(ordinal: Long): DigestOrdinal {
            check(ordinal >= 0)
            return DigestOrdinal(-(ordinal + 1))
        }
    }


    fun wasAdded(): Boolean {
        return value < 0
    }


    fun ordinal(): Long {
        return when {
            value >= 0 -> value
            else -> -value - 1
        }
    }
}