package tech.kzen.auto.server.objects.report.exec.input.connect.file

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.FilePathJvm.toPath
import tech.kzen.auto.server.objects.report.exec.input.connect.FlatDataSource
import tech.kzen.auto.server.objects.report.exec.input.connect.FlatDataStream
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataLocation
import java.nio.file.Files


class FileFlatDataSource: FlatDataSource {
    companion object {
        val instance = FileFlatDataSource()
    }


    override fun open(
        flatDataLocation: FlatDataLocation
    ): FlatDataStream {
        val filePath = flatDataLocation.dataLocation.filePath
            ?: throw IllegalArgumentException("File expected: ${flatDataLocation.dataLocation}")

        val path = filePath.toPath()
        val isText = ! flatDataLocation.dataEncoding.isBinary()
        return FileFlatDataStream(
            path, bomPrefix = isText)
    }


    override fun size(dataLocation: DataLocation): Long {
        val filePath = dataLocation.filePath
            ?: throw IllegalArgumentException("File expected: $dataLocation")

        return Files.size(filePath.toPath())
    }
}