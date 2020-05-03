package tech.kzen.auto.util

import java.nio.file.Path
import java.nio.file.Paths


object WorkUtils {
    val workDir = Paths.get("work")


    fun resolve(relativePath: Path): Path {
        check(! relativePath.isAbsolute)
        return workDir.resolve(relativePath)
    }
}