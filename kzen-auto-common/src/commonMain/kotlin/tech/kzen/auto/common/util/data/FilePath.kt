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
                    if (!trimmed.endsWith('"')) {
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
                if (!parts[2].all { isLegalInFilename(it) }) {
                    return null
                }

                if (parts.size == 3) {
                    builder.add("\\\\" + parts[2])
                    firstFileInPath = 3
                }
                else {
                    if (!parts[3].all { isLegalInFilename(it) }) {
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
                if (!part.all { isLegalInFilename(it) }) {
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
                !location.contains('/')
    }


    fun isUnixRoot(): Boolean {
        return location == "/"
    }


    /**
     * The last segment — the file or directory this path names.
     *
     * The two Windows roots answer with themselves rather than with an empty segment: a drive root as `C:`
     * (dropping the separator [parse] preserves, since `C:` alone is not a path), a network share as its share
     * name, reached across the backslash its `\\host\share` prefix keeps. Everything else is a plain
     * last-`/` split, because [parse] normalizes every other separator.
     */
    fun fileName(): String {
        if (isWindowsDriveRoot()) {
            return location.substring(0, 2)
        }

        if (isWindowsNetworkShare()) {
            return location.substring(location.lastIndexOf('\\') + 1)
        }

        val lastSeparator = location.lastIndexOf('/')

        return when (lastSeparator) {
            -1 -> location
            else -> location.substring(lastSeparator + 1)
        }
    }


    /**
     * The containing directory, or null where there is none *within the path itself*: a drive root (`C:/`), the
     * unix root (`/`), a bare network host (`\\server`), or a lone relative segment (`data.csv`).
     *
     * [parse] leaves a separator only on the two roots and normalizes every other one to `/`, so this is a plain
     * last-separator split. The one exception is the network form, whose `\\host\share` prefix keeps backslashes
     * — climbing off the share therefore looks for that separator instead.
     */
    fun parent(): FilePath? {
        if (isRoot()) {
            return null
        }

        val lastSeparator = location.lastIndexOf('/')

        return when {
            lastSeparator == -1 ->
                when (type) {
                    FilePathType.NetworkWindows -> {
                        val hostSeparator = location.lastIndexOf('\\')
                        // <= 1 is the `\\host` form: the separators found are the UNC prefix, not a share.
                        if (hostSeparator <= 1) {
                            null
                        }
                        else {
                            of(location.substring(0, hostSeparator))
                        }
                    }

                    else ->
                        null
                }

            lastSeparator == 0 ->
                of("/")

            // `C:/foo` — the drive root keeps its separator, since `C:` alone is not a path.
            type == FilePathType.AbsoluteWindows && lastSeparator == 2 ->
                of(location.take(lastSeparator + 1))

            else ->
                of(location.take(lastSeparator))
        }
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

        return location == other.location
    }


    override fun hashCode(): Int {
        return location.hashCode()
    }
}
