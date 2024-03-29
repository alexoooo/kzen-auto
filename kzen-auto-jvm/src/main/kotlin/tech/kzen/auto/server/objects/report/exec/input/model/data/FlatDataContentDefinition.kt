package tech.kzen.auto.server.objects.report.exec.input.model.data

import tech.kzen.auto.plugin.definition.ReportDefinition
import tech.kzen.auto.server.objects.report.exec.input.connect.FlatDataSource
import tech.kzen.auto.server.objects.report.exec.input.connect.FlatDataStream


data class FlatDataContentDefinition<T>(
    val flatDataInfo: FlatDataInfo,
    val flatDataSource: FlatDataSource,
    val reportDefinition: ReportDefinition<T>
) {
    fun open(): FlatDataStream {
        return flatDataSource.open(flatDataInfo.flatDataLocation)
    }

    fun size(): Long {
        return flatDataSource.size(flatDataInfo.flatDataLocation.dataLocation)
    }
}
