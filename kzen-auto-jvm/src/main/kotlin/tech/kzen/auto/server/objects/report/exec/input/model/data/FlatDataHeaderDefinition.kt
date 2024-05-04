package tech.kzen.auto.server.objects.report.exec.input.model.data

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.api.managed.TraversableReportOutput
import tech.kzen.auto.plugin.definition.ReportDefinition
import tech.kzen.auto.server.objects.report.exec.input.ReportInputChain
import tech.kzen.auto.server.objects.report.exec.input.connect.FlatDataSource
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportInputReader


data class FlatDataHeaderDefinition<T>(
    val flatDataLocation: FlatDataLocation,
    val flatDataSource: FlatDataSource,
    val reportDefinition: ReportDefinition<T>
) {
    fun openInputChain(dataBlockSize: Int): ReportInputChain<T> {
        val flatDataStream = flatDataSource.open(flatDataLocation)

        val testEncodingOrNull = flatDataLocation.dataEncoding.textEncoding?.getOrDefault()

        return ReportInputChain(
            ReportInputReader(flatDataStream),
            reportDefinition.reportDataDefinition,
            testEncodingOrNull,
            dataBlockSize)
    }


    fun extract(traversable: TraversableReportOutput<T>): HeaderListing {
        val headerExtractor = reportDefinition.headerExtractorFactory()
        val columnNames = headerExtractor.extract(traversable)
        return HeaderListing.of(columnNames)
    }
}