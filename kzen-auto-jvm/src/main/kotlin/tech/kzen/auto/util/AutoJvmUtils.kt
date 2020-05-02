package tech.kzen.auto.util

import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths


object AutoJvmUtils {
    fun parsePath(asString: String): Path? {
        try {
            return Paths.get(asString)
        }
        catch (ignored: InvalidPathException) {}

        val trimmed = asString.trim()
        try {
            return Paths.get(trimmed)
        }
        catch (ignored: InvalidPathException) {}

        if (trimmed.startsWith("\"") &&
                trimmed.endsWith("\"") &&
                trimmed.length > 2)
        {
            val unQuoted = trimmed.substring(1, trimmed.length - 1)
            try {
                return Paths.get(unQuoted)
            }
            catch (ignored: InvalidPathException) {}
        }

        return null
    }
}