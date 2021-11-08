package tech.kzen.auto.server.objects.report.exec.input.model.instance

import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.plugin.definition.ReportDataDefinition


class ReportDataInstance<Output>(
    definition: ReportDataDefinition<Output>
) {
    val dataFramer: DataFramer = definition.dataFramerFactory()

    val segments: List<ReportSegmentInstance<*, *>> =
        definition.segments.map { ReportSegmentInstance(it) }
}