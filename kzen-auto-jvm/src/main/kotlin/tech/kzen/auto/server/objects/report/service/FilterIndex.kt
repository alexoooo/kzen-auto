package tech.kzen.auto.server.objects.report.service

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.util.Digest
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
    fun inputIndexPath(
        dataLocation: DataLocation,
        processorPluginCoordinate: PluginCoordinate
    ): Path {
        val digest = Digest.Builder()
            .addDigestible(dataLocation)
            .addDigestible(processorPluginCoordinate.asCommon())
            .digest()
            .asString()

        val pathInWork = Paths.get("$indexDirName/$digest")
        val workPath = workUtils.resolve(pathInWork)

        if (! Files.isDirectory(workPath)) {
            Files.createDirectories(workPath)
        }

        return workPath
    }
}