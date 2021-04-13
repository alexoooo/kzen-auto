package tech.kzen.auto.common.objects.document.plugin.model


data class CommonDataEncodingSpec(
    val textEncoding: CommonTextEncodingSpec?
) {
    fun isBinary(): Boolean {
        return textEncoding == null
    }
}