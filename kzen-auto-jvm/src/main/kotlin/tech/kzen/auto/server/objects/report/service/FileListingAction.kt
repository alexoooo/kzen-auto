package tech.kzen.auto.server.objects.report.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import tech.kzen.auto.common.objects.document.report.listing.InputDataInfo
import tech.kzen.auto.common.objects.document.report.listing.InputSelectedInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.auto.common.util.data.FilePath
import tech.kzen.auto.common.util.data.FilePathJvm.toPath
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.plugin.PluginUtils.asPluginCoordinate
import tech.kzen.auto.server.objects.report.model.GroupPattern
import tech.kzen.auto.server.objects.report.service.ReportUtils.asCommon
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
            .lowercase()
            .split(Regex("\\s+"))

        return { path: Path? ->
            val normalizedPath = path!!.fileName.toString().lowercase()
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
            builder.sort()
            builder
        }
    }


    fun selectionInfo(inputSelectionSpec: InputSelectionSpec, groupPattern: GroupPattern): InputSelectedInfo {
        val locations = mutableListOf<InputDataInfo>()
        for (inputDataSpec in inputSelectionSpec.locations) {
            val path = inputDataSpec.location.filePath!!.toPath()
            val dataLocationInfo = toFileInfo(path)

            val processorDefinitionMetadata = KzenAutoContext.global().definitionRepository.metadata(
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