package tech.kzen.auto.server.objects.report.pipeline.input.v2.model

import tech.kzen.auto.plugin.definition.ProcessorDataDefinition
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataSource


data class FlatDataContentDefinition(
    val flatDataInfo: FlatDataInfo,
    val flatDataSource: FlatDataSource,
    val processorDataDefinition: ProcessorDataDefinition<*>
)
