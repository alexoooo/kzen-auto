package tech.kzen.auto.common.objects.document.plugin.model


data class CommonDataEncodingSpec(
    val textEncoding: CommonTextEncodingSpec?
) {
    companion object {
        private const val charsetBinary = "binary"
        private val binary = CommonDataEncodingSpec(null)

        fun ofString(asString: String): CommonDataEncodingSpec {
            if (asString == charsetBinary) {
                return binary
            }

            return CommonDataEncodingSpec(CommonTextEncodingSpec(asString))
        }
    }


    fun isBinary(): Boolean {
        return textEncoding == null
    }


    fun asString(): String {
        return textEncoding?.charsetName ?: charsetBinary
    }
}