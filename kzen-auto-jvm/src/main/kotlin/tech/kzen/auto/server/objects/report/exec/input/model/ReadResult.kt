package tech.kzen.auto.server.objects.report.exec.input.model


//@Suppress("EXPERIMENTAL_FEATURE_WARNING")
//@OptIn(ExperimentalUnsignedTypes::class)
@JvmInline
value class ReadResult constructor(
    private val value: ULong
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val endOfDataSentinel = ULong.MAX_VALUE

        val endOfData = ReadResult(endOfDataSentinel)


        fun ofInputStream(read: Int): ReadResult {
            return when (read) {
                -1 -> endOfData
                else -> ofRaw(read)
            }
        }


        fun ofRaw(byteCount: Int): ReadResult {
            return of(byteCount, byteCount)
        }


        fun of(byteCount: Int, rawByteCount: Int): ReadResult {
            require(byteCount >= 0)
            require(rawByteCount >= 0)
            val upper = byteCount.toULong().shl(Int.SIZE_BITS)
            val lower = rawByteCount.toULong()
            return ReadResult(upper or lower)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEndOfData(): Boolean {
        return value == endOfDataSentinel
    }


    fun byteCount(): Int {
        if (isEndOfData()) {
            return 0;
        }
        return (value shr Int.SIZE_BITS).toInt()
    }


    fun rawByteCount(): Int {
        if (isEndOfData()) {
            return 0;
        }
        return value.toInt()
    }


    override fun toString(): String {
        return when {
            isEndOfData() ->
                "EOD"

            byteCount() == rawByteCount() ->
                byteCount().toString()

            else ->
                "${byteCount()} (${rawByteCount()})"
        }
    }
}