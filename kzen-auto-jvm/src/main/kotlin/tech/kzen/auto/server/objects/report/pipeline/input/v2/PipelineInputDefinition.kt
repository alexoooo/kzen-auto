package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.server.objects.report.pipeline.input.connect.FlatData
import java.nio.charset.Charset


data class PipelineInputDefinition(
    val flatData: FlatData,
    val charset: Charset?,
    val dataFramerFactory: () -> DataFramer/*,
    val modelFactory: () -> DataRecordEvent*/
)