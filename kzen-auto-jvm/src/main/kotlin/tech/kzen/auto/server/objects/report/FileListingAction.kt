package tech.kzen.auto.server.objects.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.auto.common.util.data.FilePath
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*


class FileListingAction {
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


    suspend fun scanInfo(pattern: DataLocation, filter: String): List<DataLocationInfo> {
        val parsed = Paths.get(pattern.asString())
            ?: return listOf()

        if (Files.isRegularFile(parsed)) {
            return listOf(toFileInfo(parsed))
        }

        if (! Files.isDirectory(parsed)) {
            return listOf()
        }

        val filterFunction = parseFilter(filter)

        return withContext(Dispatchers.IO) {
            val builder = mutableListOf<DataLocationInfo>()
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


    fun listInfo(paths: List<DataLocation>): List<DataLocationInfo> {
        val files = paths.map { Paths.get(it.asString()) }

        return files.map {
            toFileInfo(it)
        }
    }


    private fun toFileInfo(path: Path): DataLocationInfo {
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
        return toFileInfo(path, attrs)
    }


    private fun toFileInfo(path: Path, attrs: BasicFileAttributes): DataLocationInfo {
        val absolutePath = DataLocation.ofFile(FilePath.of(path.toAbsolutePath().normalize().toString()))
        val filename = path.fileName.toString()
        val modified = Instant.fromEpochMilliseconds(
            attrs.lastModifiedTime().toMillis())

        return when {
            attrs.isDirectory ->
                DataLocationInfo.ofDirectory(absolutePath, filename, modified)

            else ->
                DataLocationInfo.ofFile(absolutePath, filename, attrs.size(), modified)
        }
    }
}