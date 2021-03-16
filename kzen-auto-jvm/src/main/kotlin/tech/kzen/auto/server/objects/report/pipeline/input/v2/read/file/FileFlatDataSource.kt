package tech.kzen.auto.server.objects.report.pipeline.input.v2.read.file

import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.DataLocationInfo
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataSource
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataStream
import java.nio.file.Paths


class FileFlatDataSource: FlatDataSource {
    override fun open(
        dataLocationInfo: DataLocationInfo
    ): FlatDataStream {
        val location = Paths.get(dataLocationInfo.dataLocation.asString())
        val isText = dataLocationInfo.dataEncoding.textEncoding != null
        return FileFlatDataStream(
            location, bomPrefix = isText)
    }
}