package tech.kzen.auto.common.util.data

import tech.kzen.auto.common.util.data.FilePathJvm.normalize


object DataLocationJvm {
    fun DataLocation.normalize(): DataLocation {
        return DataLocation(filePath?.normalize(), url?.normalize())
    }
}