package tech.kzen.auto.server.objects.report.exec.input.model.instance

import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.plugin.definition.ProcessorDataDefinition


class ProcessorDataInstance<Output>(
    definition: ProcessorDataDefinition<Output>
) {
    val dataFramer: DataFramer = definition.dataFramerFactory()

//    val outputModelType: Class<Output> = definition.outputModelType

    val segments: List<ProcessorSegmentInstance<*, *>> =
        definition.segments.map { ProcessorSegmentInstance(it) }
}