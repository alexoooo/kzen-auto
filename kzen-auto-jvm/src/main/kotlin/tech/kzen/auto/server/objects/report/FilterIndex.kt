package tech.kzen.auto.server.objects.report

import tech.kzen.auto.common.objects.document.report.listing.DataLocation
import tech.kzen.auto.server.util.AutoJvmUtils
import tech.kzen.auto.server.util.WorkUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


class FilterIndex(
    private val workUtils: WorkUtils
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val indexDirName = "index"
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun inputIndexPath(dataLocation: DataLocation): Path {
        val fileName = dataLocation.fileName()

        val indexSubPath = AutoJvmUtils.sanitizeFilename(fileName)

        val pathInWork = Paths.get("${indexDirName}/$indexSubPath")
        val workPath = workUtils.resolve(pathInWork)

        if (! Files.isDirectory(workPath)) {
            Files.createDirectories(workPath)
        }

        return workPath
    }
}