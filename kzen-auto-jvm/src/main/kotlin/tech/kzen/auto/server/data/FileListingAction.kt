package tech.kzen.auto.server.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.kzen.auto.common.objects.document.report.listing.InputDataInfo
import tech.kzen.auto.common.objects.document.report.listing.InputSelectedInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.auto.common.util.data.DataListing
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.auto.common.util.data.DataLocationJvm.normalize
import tech.kzen.auto.common.util.data.FilePath
import tech.kzen.auto.common.util.data.FilePathJvm.toPath
import tech.kzen.auto.server.objects.plugin.PluginUtils.asPluginCoordinate
import tech.kzen.auto.server.objects.report.model.GroupPattern
import tech.kzen.auto.server.objects.report.service.ReportUtils
import tech.kzen.auto.server.objects.report.service.ReportUtils.asCommon
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.time.Instant


class FileListingAction(
    private val definitionRepository: ReportDefinitionRepository
) {
    private fun parseFilter(filter: String): (Path) -> Boolean {
        val trimmedFilter = filter.trim()

        if (trimmedFilter.isEmpty()) {
            return { true }
        }

        val filterParts: List<String> = trimmedFilter
            .lowercase()
            .split(Regex("\\s+"))

        return { path: Path? ->
            val normalizedPath = path!!.fileName.toString().lowercase()
            filterParts.all { normalizedPath.contains(it) }
        }
    }


    suspend fun scanInfo(pattern: DataLocation, filter: String): List<DataLocationInfo> {
        return withContext(Dispatchers.IO) {
            scanInfoBlocking(pattern, filter)
        }
    }


    /**
     * Lists one directory for an interactive chooser. Immediate directories are always included so an active
     * filename filter cannot hide navigation; directories sort before the matching files.
     *
     * Runtime source discovery remains [scanInfo], whose contract is files-only.
     */
    suspend fun browseInfo(directory: DataLocation, filter: String): List<DataLocationInfo> {
        return withContext(Dispatchers.IO) {
            browseInfoBlocking(directory, filter)
        }
    }


    /**
     * [browseInfo] plus the absolute directory it read, for a chooser that has to show where it is.
     *
     * A relative request — `./`, the notation default — resolves against the server's working directory, which the
     * client cannot know and cannot navigate up out of. Report answers its own browse with the same pair.
     */
    suspend fun browseListing(directory: DataLocation, filter: String): DataListing {
        return DataListing(
            directory.normalize(),
            browseInfo(directory, filter))
    }


    fun browseInfoBlocking(directory: DataLocation, filter: String): List<DataLocationInfo> {
        val parsed = Paths.get(directory.asString())
            ?: return emptyList()

        if (Files.isRegularFile(parsed)) {
            return listOf(toFileInfo(parsed))
        }

        if (!Files.isDirectory(parsed)) {
            return emptyList()
        }

        val filterFunction = parseFilter(filter)
        val directories = mutableListOf<DataLocationInfo>()
        val files = mutableListOf<DataLocationInfo>()
        Files.newDirectoryStream(parsed).use { children ->
            for (child in children) {
                val attrs = Files.readAttributes(child, BasicFileAttributes::class.java)
                if (attrs.isDirectory) {
                    directories.add(toFileInfo(child, attrs))
                }
                else if (filterFunction(child)) {
                    files.add(toFileInfo(child, attrs))
                }
            }
        }
        directories.sort()
        files.sort()
        return directories + files
    }


    fun scanInfoBlocking(pattern: DataLocation, filter: String): List<DataLocationInfo> {
        val parsed = Paths.get(pattern.asString())
            ?: return listOf()

        if (Files.isRegularFile(parsed)) {
            return listOf(toFileInfo(parsed))
        }

        if (!Files.isDirectory(parsed)) {
            return listOf()
        }

        val filterFunction = parseFilter(filter)

        val builder = mutableListOf<DataLocationInfo>()
        Files.walkFileTree(
            parsed,
            EnumSet.noneOf(FileVisitOption::class.java),
            1,
            object: SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!attrs.isDirectory && filterFunction(file)) {
                        builder.add(toFileInfo(file, attrs))
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        builder.sort()
        return builder
    }


    fun fileInfoBlocking(location: DataLocation): DataLocationInfo {
        return toFileInfo(Paths.get(location.asString()))
    }


    fun selectionInfo(inputSelectionSpec: InputSelectionSpec, groupPattern: GroupPattern): InputSelectedInfo {
        val locations = mutableListOf<InputDataInfo>()
        for (inputDataSpec in inputSelectionSpec.locations) {
            val path = inputDataSpec.location.filePath!!.toPath()
            val dataLocationInfo = toFileInfo(path)

            val processorDefinitionMetadata = definitionRepository.metadata(
                inputDataSpec.processorDefinitionCoordinate.asPluginCoordinate())

            val dataEncodingSpec = ReportUtils.encoding(inputDataSpec, processorDefinitionMetadata)
            val commonDataEncodingSpec = dataEncodingSpec?.asCommon()

            val invalidProcessor =
                processorDefinitionMetadata?.payloadType != inputSelectionSpec.dataType

            val dataLocationGroup =
                groupPattern.extract(dataLocationInfo.name)

            locations.add(InputDataInfo(
                dataLocationInfo,
                inputDataSpec.processorDefinitionCoordinate,
                commonDataEncodingSpec,
                dataLocationGroup,
                invalidProcessor
            ))
        }
        locations.sort()
        return InputSelectedInfo(locations)
    }


    private fun toFileInfo(path: Path): DataLocationInfo {
        val attrs =
            try {
                Files.readAttributes(path, BasicFileAttributes::class.java)
            }
            catch (e: NoSuchFileException) {
                null
            }

        return toFileInfo(path, attrs)
    }


    private fun toFileInfo(path: Path, attrs: BasicFileAttributes?): DataLocationInfo {
        val absolutePathString = path.toAbsolutePath().normalize().toString()
        val absoluteFilePath = FilePath.of(absolutePathString)
        val absoluteDataLocation = DataLocation.ofFile(absoluteFilePath)
        val filename = path.fileName.toString()

        return when {
            attrs != null -> {
                val modified = Instant.fromEpochMilliseconds(
                    attrs.lastModifiedTime().toMillis())

                when {
                    attrs.isDirectory ->
                        DataLocationInfo.ofDirectory(absoluteDataLocation, filename, modified)

                    else ->
                        DataLocationInfo.ofFile(absoluteDataLocation, filename, attrs.size(), modified)
                }
            }

            else ->
                DataLocationInfo.ofMissingFile(absoluteDataLocation, filename)
        }
    }
}
