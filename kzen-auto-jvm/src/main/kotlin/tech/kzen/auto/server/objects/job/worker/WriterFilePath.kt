package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.objects.report.exec.calc.ColumnValue
import java.nio.file.Files
import java.nio.file.Path


/** Writer-only path and finalized-file contract; reader path parsing remains in [toFilePath]. */
internal object WriterFilePath {
    fun resolve(raw: String): Path =
        toFilePath(raw).toAbsolutePath().normalize()


    fun prepare(path: Path) {
        path.parent?.let(Files::createDirectories)
    }


    suspend fun finalizedRef(
        path: Path,
        control: JobControl,
        fileListingAction: FileListingAction
    ): DataRef {
        val info = control.runBlockingIo {
            fileListingAction.fileInfoBlocking(DataLocation.of(path.toString()))
        }
        require(!info.directory && info.size >= 0) {
            "Writer output is missing or is a directory: $path"
        }
        return DataRef.of(info)
    }


    fun parameterText(name: String, value: Any?): String {
        return when (value) {
            is String -> value
            is Char, is Boolean -> value.toString()
            is Number -> ColumnValue.toText(ColumnValue.ofScalar(value))
            null -> throw IllegalArgumentException("Writer path parameter '$name' is null")
            else -> throw IllegalArgumentException(
                "Writer path parameter '$name' must be scalar; found ${value::class.qualifiedName}")
        }
    }
}
