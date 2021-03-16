package tech.kzen.auto.plugin.spec


/**
 * leave textEncoding as null for binary
 */
data class DataEncodingSpec(
    val textEncoding: TextEncodingSpec?
) {
    companion object {
        val binary = DataEncodingSpec(null)
        val utf8 = DataEncodingSpec(TextEncodingSpec.utf8)
    }


    fun isBinary(): Boolean {
        return textEncoding == null
    }
}