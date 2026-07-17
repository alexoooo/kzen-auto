package tech.kzen.auto.common.util.data

import tech.kzen.auto.common.util.data.FilePathJvm.normalize
import tech.kzen.auto.platform.UrlJvm.normalize


object DataLocationJvm {
    fun DataLocation.normalize(): DataLocation {
        return DataLocation(filePath?.normalize(), url?.normalize())
    }
}