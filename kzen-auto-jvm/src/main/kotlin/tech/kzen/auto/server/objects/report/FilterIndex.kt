package tech.kzen.auto.server.objects.report

import tech.kzen.auto.util.AutoJvmUtils
import tech.kzen.auto.util.WorkUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


object FilterIndex {
    //-----------------------------------------------------------------------------------------------------------------
    const val indexDirName = "index"


    //-----------------------------------------------------------------------------------------------------------------
    fun inputIndexPath(absoluteInputPath: Path): Path {
        val indexSubPath = AutoJvmUtils.sanitizeFilename(
            absoluteInputPath.fileName.toString())

        val pathInWork = Paths.get("${indexDirName}/$indexSubPath")
        val workPath = WorkUtils.resolve(pathInWork)

        if (! Files.isDirectory(workPath)) {
            Files.createDirectories(workPath)
        }

        return workPath
    }
}