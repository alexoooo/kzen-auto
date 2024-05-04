package tech.kzen.auto.server.objects.report.service

import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataHeaderDefinition
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportHeaderReader
import java.nio.file.Files
import java.nio.file.Path


class ColumnListingAction(
    private val filterIndex: FilterIndex
) {
    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("ConstPropertyName")
    companion object {
        private const val columnsCsvFilename = "columns.csv"

        private const val columnsCsvHeader = "Number,Label,Occurrence"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun cachedHeaderListing(
        columnsFile: Path
    ): HeaderListing? {
        if (! Files.exists(columnsFile)) {
            return null
        }

        val text = Files.readString(columnsFile, Charsets.UTF_8)

        return CsvReportDefiner
            .literal(text)
            .drop(1)
            .map {
                HeaderLabel(
                    it.getString(1),
                    it.getString(2).toInt())
            }
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
                FlatFileRecord.of(
                    it.index.toString(),
                    it.value.text,
                    it.value.occurrence.toString()
                ).toCsv()
            }

        val csvFile = "$columnsCsvHeader\n$csvBody"

        Files.writeString(columnsFile, csvFile)

        return headerListing
    }


    private fun <T> extractColumnNames(
        flatDataHeaderDefinition: FlatDataHeaderDefinition<T>
    ): HeaderListing {
        return ReportHeaderReader().extract(flatDataHeaderDefinition)
    }
}