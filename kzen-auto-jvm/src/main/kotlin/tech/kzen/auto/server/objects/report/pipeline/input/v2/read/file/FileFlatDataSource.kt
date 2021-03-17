package tech.kzen.auto.server.objects.report.pipeline.input.v2.read.file

import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.FlatDataLocation
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataSource
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataStream
import java.nio.file.Paths


class FileFlatDataSource: FlatDataSource {
    override fun open(
        flatDataLocation: FlatDataLocation
    ): FlatDataStream {
        val location = Paths.get(flatDataLocation.dataLocation.asString())
        val isText = flatDataLocation.dataEncoding.textEncoding != null
        return FileFlatDataStream(
            location, bomPrefix = isText)
    }
}