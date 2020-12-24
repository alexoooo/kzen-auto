package tech.kzen.auto.server.service.compile

import tech.kzen.lib.common.util.Digest


data class KotlinCode(
    val packagePath: String,
    val mainClassName: String,
    val fileSourceCode: String
) {
    fun signature(): String {
        val codeDigest = Digest.ofUtf8(fileSourceCode)
        return "${mainClassName}_$codeDigest"
    }

    fun fullyQualifiedMainClass(): String {
        return "$packagePath.$mainClassName"
    }
}
