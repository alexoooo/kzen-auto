package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class FilePath(
    val location: String
): Digestible {
    companion object {
        fun of(location: String): FilePath {
            return parse(location)
                ?: throw IllegalArgumentException("Invalid: $location")
        }

        fun parse(location: String): FilePath? {
            return FilePath(location)
        }
    }


    override fun digest(builder: Digest.Builder) {
        builder.addUtf8(location)
    }
}
