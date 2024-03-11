package tech.kzen.auto.common.util.data

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


class FilePath private constructor(
    val location: String,
    val type: FilePathType
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun of(location: String): FilePath {
            return parse(location)
                ?: throw IllegalArgumentException("Invalid: $location")
        }

        fun parse(location: String): FilePath? {
            val trimmed = location.trim()

            val withoutQuotes =
                if (trimmed.startsWith('"')) {
                    if (! trimmed.endsWith('"')) {
                        return null
                    }
                    trimmed.substring(1, trimmed.length - 1)
                }
                else {
                    trimmed
                }

            if (withoutQuotes.length <= 1) {
                return when {
                    withoutQuotes == "/" ->
                        FilePath(withoutQuotes, FilePathType.AbsoluteUnix)

                    withoutQuotes.isNotEmpty() && isLegalInFilename(withoutQuotes[0]) || withoutQuotes == "." ->
                        FilePath(withoutQuotes, FilePathType.Relative)

                    else ->
                        null
                }
            }

            val normalizedSlashes = withoutQuotes.replace('\\', '/')

            val type = when {
                normalizedSlashes[1] == ':' && isWindowsDriveLetter(normalizedSlashes[0]) ->
                    if (normalizedSlashes.length > 2 && normalizedSlashes[2] != '/') {
                        return null
                    }
                    else {
                        FilePathType.AbsoluteWindows
                    }

                normalizedSlashes.contains(':') ->
                    return null

                withoutQuotes.startsWith("\\\\") ->
                    if (withoutQuotes.length == 2) {
                        return null
                    }
                    else {
                        FilePathType.NetworkWindows
                    }

                normalizedSlashes.startsWith("/") ->
                    FilePathType.AbsoluteUnix

                else ->
                    FilePathType.Relative
            }

            if (type == FilePathType.AbsoluteWindows && normalizedSlashes.length == 2) {
                return FilePath("$normalizedSlashes/", type)
            }
            else if (type == FilePathType.AbsoluteWindows && normalizedSlashes.length == 3 ||
                    type == FilePathType.AbsoluteUnix && normalizedSlashes.length == 1) {
                return FilePath(normalizedSlashes, type)
            }

            val parts = normalizedSlashes.split('/')
            val builder = mutableListOf<String>()

            val firstFileInPath: Int
            if (type == FilePathType.NetworkWindows) {
                if (! parts[2].all { isLegalInFilename(it) }) {
                    return null
                }

                if (parts.size == 3) {
                    builder.add("\\\\" + parts[2])
                    firstFileInPath = 3
                }
                else {
                    if (! parts[3].all { isLegalInFilename(it) }) {
                        return null
                    }
                    builder.add("\\\\" + parts[2] + "\\" + parts[3])
                    firstFileInPath = 4
                }
            }
            else if (type == FilePathType.AbsoluteWindows) {
                builder.add(parts[0])
                firstFileInPath = 1
            }
            else if (type == FilePathType.AbsoluteUnix) {
                builder.add("")
                firstFileInPath = 1
            }
            else {
                firstFileInPath = 0
            }

            for (i in firstFileInPath until parts.size) {
                val part = parts[i]
                if (! part.all { isLegalInFilename(it) }) {
                    return null
                }

                if (part.isNotEmpty()) {
                    builder.add(part)
                }
            }

            val normalizedDirs = builder.joinToString("/")
            return FilePath(normalizedDirs, type)
        }


        private fun isWindowsDriveLetter(character: Char): Boolean {
            return character in 'a'..'z' ||
                    character in 'A'..'Z'
        }


        private fun isLegalInFilename(character: Char): Boolean {
            return when (character) {
                '/', '\\', ':', '*', '?', '"', '<', '>', '|' -> false
                else -> true
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isRoot(): Boolean {
        return isWindowsDriveRoot() || isUnixRoot()
    }


    fun isWindowsDriveRoot(): Boolean {
        return type == FilePathType.AbsoluteWindows &&
                location.length <= 3
    }


    fun isWindowsNetworkHost(): Boolean {
        return type == FilePathType.NetworkWindows &&
                location.lastIndexOf('\\') <= 1
    }


    fun isWindowsNetworkShare(): Boolean {
        return type == FilePathType.NetworkWindows &&
                location.lastIndexOf('\\') > 1 &&
                ! location.contains('/')
    }


    fun isUnixRoot(): Boolean {
        return location == "/"
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(location)
    }


    override fun toString(): String {
        return location
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FilePath

        if (location != other.location) return false

        return true
    }


    override fun hashCode(): Int {
        return location.hashCode()
    }
}
