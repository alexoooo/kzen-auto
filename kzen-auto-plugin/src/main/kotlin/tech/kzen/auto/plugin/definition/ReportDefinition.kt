package tech.kzen.auto.plugin.definition

import tech.kzen.auto.plugin.api.HeaderExtractor


data class ReportDefinition<Output>(
    val reportDataDefinition: ReportDataDefinition<Output>,
    val headerExtractorFactory: () -> HeaderExtractor<Output>
)