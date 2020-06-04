package tech.kzen.auto.server.objects.filter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.kzen.auto.util.AutoJvmUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


object FileListingAction {
    suspend fun list(pattern: String): List<Path>? {
        val parsed = AutoJvmUtils.parsePath(pattern)

        if (parsed != null) {
            if (Files.isRegularFile(parsed)) {
                return listOf(parsed)
            }

            if (Files.isDirectory(parsed)) {
                return withContext(Dispatchers.IO) {
                    Files.newDirectoryStream(parsed)
                            .use { it.toList() }
                }
            }

            return null
        }

        val trimmed = pattern.trim()

        val unQuoted =
                if (trimmed.startsWith("\"") &&
                        trimmed.endsWith("\"") &&
                        trimmed.length > 2)
                {
                     trimmed.substring(1, trimmed.length - 1)
                }
                else {
                    trimmed
                }

        val normalized = unQuoted.replace("\\", "/")
        val lastDirDelimiterIndex = normalized.lastIndexOf('/')
        if (lastDirDelimiterIndex == -1) {
            return null
        }

        val parent = normalized.substring(0, lastDirDelimiterIndex)
        val dir = Paths.get(parent)
        val glob = normalized.substring(lastDirDelimiterIndex + 1)

        return withContext(Dispatchers.IO) {
            Files.newDirectoryStream(dir, glob)
                    .use { it.toList() }
        }
    }
}