package tech.kzen.auto.server.service.compile

import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.util.Digest


data class KotlinCode(
    val packagePath: String,
    val mainClassName: String,
    val fileSourceCode: String
) {
    fun signature(): String {
        val codeDigest = Digest.ofUtf8(fileSourceCode)
        val encoded = WorkUtils.filenameEncodeDigest(codeDigest)
        return "${mainClassName}_$encoded"
    }

    fun fullyQualifiedMainClass(): String {
        return "$packagePath.$mainClassName"
    }
}
