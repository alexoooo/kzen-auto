package tech.kzen.auto.plugin.spec

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


data class TextEncodingSpec(
    val charset: Charset? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val defaultCharset = StandardCharsets.UTF_8
        val utf8 = TextEncodingSpec(defaultCharset)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun getOrDefault(): Charset {
        return charset ?: defaultCharset
    }
}