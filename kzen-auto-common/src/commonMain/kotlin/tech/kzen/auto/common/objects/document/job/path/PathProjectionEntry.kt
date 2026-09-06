package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


/** One projected column: a [path] and an optional output-name [alias] (blank = the path's default name). */
data class PathProjectionEntry(
    val path: ProjectionPath,
    val alias: String? = null
): Digestible {
    val outputName: String
        get() = alias?.takeIf { it.isNotBlank() } ?: path.defaultOutputName()


    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(path)
        sink.addUtf8Nullable(alias)
    }
}
