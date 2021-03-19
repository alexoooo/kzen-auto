package tech.kzen.auto.server.objects.report.pipeline.input.model.data

import tech.kzen.auto.plugin.definition.ProcessorDataDefinition
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FlatDataSource
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FlatDataStream


data class FlatDataContentDefinition<T>(
    val flatDataInfo: FlatDataInfo,
    val flatDataSource: FlatDataSource,
    val processorDataDefinition: ProcessorDataDefinition<T>
) {
    fun open(): FlatDataStream {
        return flatDataSource.open(flatDataInfo.flatDataLocation)
    }

    fun size(): Long {
        return flatDataSource.size(flatDataInfo.flatDataLocation.dataLocation)
    }
}
