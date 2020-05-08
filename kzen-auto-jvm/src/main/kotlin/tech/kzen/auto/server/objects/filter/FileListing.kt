package tech.kzen.auto.server.objects.filter

import tech.kzen.auto.util.AutoJvmUtils
import java.nio.file.Path


object FileListing {
    suspend fun list(pattern: String): List<Path>? {
        val parsed = AutoJvmUtils.parsePath(pattern)
            ?: return null

        return listOf(parsed)
    }
}