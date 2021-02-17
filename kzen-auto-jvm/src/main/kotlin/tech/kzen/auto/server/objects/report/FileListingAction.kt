package tech.kzen.auto.server.objects.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import tech.kzen.auto.common.objects.document.report.listing.FileInfo
import tech.kzen.auto.util.AutoJvmUtils
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*


class FileListingAction {
//    suspend fun scan(pattern: String): List<Path>? {
//        val parsed = AutoJvmUtils.parsePath(pattern)
//
//        if (parsed != null) {
//            if (Files.isRegularFile(parsed)) {
//                return listOf(parsed)
//            }
//
//            if (Files.isDirectory(parsed)) {
//                return withContext(Dispatchers.IO) {
//                    Files.newDirectoryStream(parsed)
//                            .use { it.toList() }
//                }
//            }
//
//            return null
//        }
//
//        val trimmed = pattern.trim()
//
//        val unQuoted =
//                if (trimmed.startsWith("\"") &&
//                        trimmed.endsWith("\"") &&
//                        trimmed.length > 2)
//                {
//                     trimmed.substring(1, trimmed.length - 1)
//                }
//                else {
//                    trimmed
//                }
//
//        val normalized = unQuoted.replace("\\", "/")
//        val lastDirDelimiterIndex = normalized.lastIndexOf('/')
//        if (lastDirDelimiterIndex == -1) {
//            return null
//        }
//
//        val parent = normalized.substring(0, lastDirDelimiterIndex)
//        val dir = Paths.get(parent)
//        val glob = normalized.substring(lastDirDelimiterIndex + 1)
//
//        return withContext(Dispatchers.IO) {
//            Files.newDirectoryStream(dir, glob)
//                    .use { it.toList() }
//        }
//    }

    
//    suspend fun scan(pattern: String, filter: String): List<Path>? {
//        val parsed = AutoJvmUtils.parsePath(pattern)
//            ?: return null
//
//        if (Files.isRegularFile(parsed)) {
//            return listOf(parsed)
//        }
//
//        if (! Files.isDirectory(parsed)) {
//            return null
//        }
//
//        val filterFunction = parseFilter(filter)
//
//        return withContext(Dispatchers.IO) {
//            Files.newDirectoryStream(parsed, filterFunction)
//                .use { it.toList() }
//        }
//    }


    private fun parseFilter(filter: String): (Path) -> Boolean {
        val trimmedFilter = filter.trim()

        if (trimmedFilter.isEmpty()) {
            return { true }
        }

        val filterParts: List<String> = trimmedFilter
            .toLowerCase()
            .split(Regex("\\s+"))

        return { path: Path? ->
            val normalizedPath = path!!.fileName.toString().toLowerCase()
            filterParts.all { normalizedPath.contains(it) }
        }
    }


    suspend fun scanInfo(pattern: String, filter: String): List<FileInfo> {
        val parsed = AutoJvmUtils.parsePath(pattern)
            ?: return listOf()

        if (Files.isRegularFile(parsed)) {
            return listOf(toFileInfo(parsed))
        }

        if (! Files.isDirectory(parsed)) {
            return listOf()
        }

        val filterFunction = parseFilter(filter)

        return withContext(Dispatchers.IO) {
            val builder = mutableListOf<FileInfo>()
            Files.walkFileTree(
                parsed,
                EnumSet.noneOf(FileVisitOption::class.java),
                1,
                object: SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (filterFunction(file)) {
                            builder.add(toFileInfo(file, attrs))
                        }
                        return FileVisitResult.CONTINUE
                    }
                })
            builder
        }
    }


    fun listInfo(paths: List<String>): List<FileInfo> {
        val files = paths.map { Paths.get(it) }

        return files.map {
            toFileInfo(it)
        }
    }


    private fun toFileInfo(path: Path): FileInfo {
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
        return toFileInfo(path, attrs)

//        val absolutePath = path.toAbsolutePath().normalize().toString()
//        val filename = path.fileName.toString()
//        val modified = Instant.fromEpochMilliseconds(
//            Files.getLastModifiedTime(path).toMillis())
//
//        return when {
//            Files.isDirectory(path) ->
//                FileInfo.ofDirectory(absolutePath, filename, modified)
//
//            else ->
//                FileInfo.ofFile(absolutePath, filename, Files.size(path), modified)
//        }
    }


    private fun toFileInfo(path: Path, attrs: BasicFileAttributes): FileInfo {
        val absolutePath = path.toAbsolutePath().normalize().toString()
        val filename = path.fileName.toString()
        val modified = Instant.fromEpochMilliseconds(
            attrs.lastModifiedTime().toMillis())

        return when {
            attrs.isDirectory ->
                FileInfo.ofDirectory(absolutePath, filename, modified)

            else ->
                FileInfo.ofFile(absolutePath, filename, attrs.size(), modified)
        }
    }
}