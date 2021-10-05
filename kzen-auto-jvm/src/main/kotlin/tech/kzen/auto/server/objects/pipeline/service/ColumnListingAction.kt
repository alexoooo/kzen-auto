package tech.kzen.auto.server.objects.pipeline.service

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.FlatDataHeaderDefinition
import tech.kzen.auto.server.objects.pipeline.exec.input.parse.csv.CsvProcessorDefiner
import tech.kzen.auto.server.objects.pipeline.exec.input.stages.ProcessorHeaderReader
import java.nio.file.Files
import java.nio.file.Path


class ColumnListingAction(
    private val filterIndex: FilterIndex
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val columnsCsvFilename = "columns.csv"
    }


    //-----------------------------------------------------------------------------------------------------------------
//    suspend fun <T> columnNamesMerge(
//        flatDataHeaderDefinitions: List<FlatDataHeaderDefinition<T>>
//    ): HeaderListing {
//        val builder = LinkedHashSet<String>()
//        for (flatDataHeaderDefinition in flatDataHeaderDefinitions) {
//            val columns = columnNames(flatDataHeaderDefinition)
//            builder.addAll(columns.values)
//        }
//        return HeaderListing(builder.toList())
//    }


    private fun cachedHeaderListing(
        columnsFile: Path
    ): HeaderListing? {
        if (! Files.exists(columnsFile)) {
            return null
        }

        val text =
//            withContext(Dispatchers.IO) {
                Files.readString(columnsFile, Charsets.UTF_8)
//            }

        return CsvProcessorDefiner
            .literal(text)
            .drop(1)
            .map { it.getString(1) }
            .let { HeaderListing(it) }
    }


    fun cachedHeaderListing(
        dataLocation: DataLocation,
        processorPluginCoordinate: PluginCoordinate
    ): HeaderListing? {
        val inputIndexPath = filterIndex.inputIndexPath(dataLocation, processorPluginCoordinate)
        val columnsFile = inputIndexPath.resolve(columnsCsvFilename)
        return cachedHeaderListing(columnsFile)
    }


    fun <T> headerListing(
        flatDataHeaderDefinition: FlatDataHeaderDefinition<T>,
        processorPluginCoordinate: PluginCoordinate
    ): HeaderListing {
        val inputIndexPath = filterIndex.inputIndexPath(
            flatDataHeaderDefinition.flatDataLocation.dataLocation,
            processorPluginCoordinate)

        val columnsFile = inputIndexPath.resolve(columnsCsvFilename)

        val cached = cachedHeaderListing(columnsFile)

        if (cached != null) {
            return cached
        }

        val headerListing = extractColumnNames(flatDataHeaderDefinition)

        val csvBody = headerListing
            .values
            .withIndex()
            .joinToString("\n") {
                FlatFileRecord.of(listOf(it.index.toString(), it.value)).toCsv()
            }

        val csvFile = "Number,Name\n$csvBody"

//        withContext(Dispatchers.IO) {
            Files.writeString(columnsFile, csvFile)
//        }

        return headerListing
    }


    private fun <T> extractColumnNames(
        flatDataHeaderDefinition: FlatDataHeaderDefinition<T>
    ): HeaderListing {
        return ProcessorHeaderReader().extract(flatDataHeaderDefinition)
    }
}