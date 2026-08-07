package tech.kzen.auto.server.service.compile

import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.util.digest.Digest
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.toScriptSource


@Suppress("ConstPropertyName")
data class KotlinCode(
    val mainClassName: String,
    val sourceText: String,
    val userCodeRegion: UserCodeRegion? = null
) {
    /**
     * Where a user-authored expression sits verbatim inside [sourceText], stated by whatever generated the
     * wrapper around it. Both bounds are needed: a diagnostic positioned outside the region describes
     * generated code, and is reported without a position rather than clamped onto text the user did not
     * write. As a POSITION the end is inclusive — a parse error legitimately points one past the last
     * character of the expression.
     *
     * Null for generated source with no user-authored region to attribute a position to.
     */
    data class UserCodeRegion(
        val offset: Int,
        val length: Int
    )


    companion object {
        const val scriptClassName = "__"
        const val classNamePrefix = "${scriptClassName}$"
    }

    // The source text alone is the key: it fully determines [userCodeRegion], so a position derived from one
    // compile stays valid for every later cache hit on the same signature.
    fun signature(): String {
        val codeDigest = Digest.ofUtf8(sourceText)
        val encoded = WorkUtils.filenameEncodeDigest(codeDigest)
        return "${mainClassName}_$encoded"
    }

    fun toScriptSource(): SourceCode {
        return sourceText.toScriptSource(scriptClassName)
    }
}
