package tech.kzen.auto.server.objects.report.pipeline.input.v2.model

import tech.kzen.auto.platform.DataLocation
import tech.kzen.auto.plugin.definition.ProcessorDefinition
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataReader


data class FlatDataDefinition(
    val dataLocation: DataLocation,
    val readerFactory: (DataLocation) -> FlatDataReader,
    val dataEncoding: DataEncodingSpec,
    val processorDefinition: ProcessorDefinition<*>
)
