package tech.kzen.auto.server.objects.report.pipeline.input.connect

import com.google.common.io.MoreFiles
import tech.kzen.auto.common.objects.document.report.listing.DataLocation
import tech.kzen.auto.common.objects.document.report.listing.FilePath
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


data class FileFlatData(
    val path: Path
): FlatData {
    override fun key(): DataLocation {
        return DataLocation.ofFile(FilePath.of(path.normalize().toAbsolutePath().toString()))
    }


    override fun outerExtension(): String {
        return MoreFiles.getFileExtension(path)
    }


    override fun innerExtension(): String {
        val outerExtension = outerExtension()
        return when (outerExtension()) {
            "gz" -> {
                val withoutExtension = MoreFiles.getNameWithoutExtension(path)
                MoreFiles.getFileExtension(Paths.get(withoutExtension))
            }

            else ->
                outerExtension
        }
    }


    override fun size(): Long {
        return Files.size(path)
    }


    override fun open(): InputStream {
        return Files.newInputStream(path)
    }
}