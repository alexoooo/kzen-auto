package tech.kzen.auto.common.data.format

import tech.kzen.auto.common.data.read.ContentCodingSpec


/**
 * How a file is read follows from its name, never from its content: a `.gz` suffix (or `.tgz`, short for
 * `.tar.gz`) means gzip, and the extension under it names the format through each format's `extensions`.
 */
object FileFormatNames {
    //-----------------------------------------------------------------------------------------------------------------
    private const val gzipSuffix = ".gz"
    private const val tgzSuffix = ".tgz"
    private const val tarExtension = "tar"


    //-----------------------------------------------------------------------------------------------------------------
    sealed interface Choice {
        data class Named(val format: ConfiguredRecordFormat): Choice
        data class Unnamed(val reason: String): Choice
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The last segment of [path], which may be a bare file name. */
    fun fileName(path: String): String =
        path.substringAfterLast('/').substringAfterLast('\\')


    fun contentCoding(path: String): ContentCodingSpec {
        val name = fileName(path).lowercase()
        return if (name.endsWith(gzipSuffix) || name.endsWith(tgzSuffix)) {
            ContentCodingSpec.gzip
        }
        else {
            ContentCodingSpec.identity
        }
    }


    /** The lower-case extension naming the format, under any gzip suffix; null when the name has none. */
    fun formatExtension(path: String): String? {
        val name = fileName(path).lowercase()
        if (name.endsWith(tgzSuffix)) {
            return tarExtension
        }
        val decoded = name.removeSuffix(gzipSuffix)
        return decoded
            .substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() && it.length < decoded.length - 1 }
    }


    /** Whether [extensions] (as a format declares them) name the format of [path]. */
    fun names(extensions: List<String>, path: String): Boolean {
        val extension = formatExtension(path)
            ?: return false
        return extensions.any { it.trim().removePrefix(".").lowercase() == extension }
    }


    /**
     * The one format among [formats] whose extensions name [path], or why there is none: no extension, no format
     * declaring it, or more than one.
     */
    fun choose(path: String, formats: List<ConfiguredRecordFormat>): Choice {
        val name = fileName(path)
        val extension = formatExtension(path)
            ?: return Choice.Unnamed("'$name' has no extension to name its format; select a format")
        val named = formats.filter { names(it.extensions, path) }
        return when (named.size) {
            1 -> Choice.Named(named.single())
            0 -> Choice.Unnamed("No format reads '.$extension' files such as '$name'; select a format")
            else -> Choice.Unnamed(
                "'.$extension' is named by more than one format (${named.joinToString { it.title }}); " +
                    "select a format for '$name'")
        }
    }
}
