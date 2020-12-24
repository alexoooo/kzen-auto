package tech.kzen.auto.server.service.compile

import com.google.common.io.BaseEncoding
import tech.kzen.lib.common.util.Digest


data class KotlinCode(
    val packagePath: String,
    val mainClassName: String,
    val fileSourceCode: String
) {
    companion object {
        private val digestEncoding = BaseEncoding.base32().omitPadding().lowerCase()
    }

    fun signature(): String {
        val codeDigest = Digest.ofUtf8(fileSourceCode)
        val encoded = digestEncoding.encode(codeDigest.toByteArray())
        return "${mainClassName}_$encoded"
    }

    fun fullyQualifiedMainClass(): String {
        return "$packagePath.$mainClassName"
    }
}
