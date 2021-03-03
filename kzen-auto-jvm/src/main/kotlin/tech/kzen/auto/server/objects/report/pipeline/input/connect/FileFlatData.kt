package tech.kzen.auto.server.objects.report.pipeline.input.connect

import com.google.common.io.MoreFiles
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


data class FileFlatData(
    val path: Path
): FlatData {
    override fun key(): URI {
        return path.toUri()
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