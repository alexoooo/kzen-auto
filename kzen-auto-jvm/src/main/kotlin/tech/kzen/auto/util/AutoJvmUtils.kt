package tech.kzen.auto.util

import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths


object AutoJvmUtils
{
    fun sanitizeFilename(filenameFragment: String): String {
        return filenameFragment
            .replace(Regex("\\W+"), "_")
    }


    fun parsePath(asString: String): Path? {
        tryParsePath(asString)
            ?.let { return it }

        val trimmed = asString.trim()
        tryParsePath(trimmed)
            ?.let { return it }

        if (trimmed.startsWith("\"") &&
                trimmed.endsWith("\"") &&
                trimmed.length > 2)
        {
            val unQuoted = trimmed.substring(1, trimmed.length - 1)
            tryParsePath(unQuoted)
                ?.let { return it }
        }

        return null
    }


    private fun tryParsePath(path: String): Path? {
        val adjusted = adjustPath(path)

        try {
            return Paths.get(adjusted)
        }
        catch (ignored: InvalidPathException) {}

        return null
    }

    private fun adjustPath(path: String): String {
        if (path.contains('/') ||
                path.contains('\\') ||
                ! path.endsWith(":")
        ) {
            return path
        }

        return "$path/"
    }
}