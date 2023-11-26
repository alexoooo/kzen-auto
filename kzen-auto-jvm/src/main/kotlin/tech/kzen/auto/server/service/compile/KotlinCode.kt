package tech.kzen.auto.server.service.compile

import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.util.digest.Digest
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.toScriptSource


@Suppress("ConstPropertyName")
data class KotlinCode(
    val mainClassName: String,
    val sourceText: String
) {
    companion object {
        const val scriptClassName = "__"
        const val classNamePrefix = "${scriptClassName}$"
    }

    fun signature(): String {
        val codeDigest = Digest.ofUtf8(sourceText)
        val encoded = WorkUtils.filenameEncodeDigest(codeDigest)
        return "${mainClassName}_$encoded"
    }

    fun toScriptSource(): SourceCode {
        return sourceText.toScriptSource(scriptClassName)
    }
}
