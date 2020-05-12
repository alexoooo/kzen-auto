package tech.kzen.auto.util

import java.nio.file.Path
import java.nio.file.Paths


object WorkUtils {
    // NB: sibling to hide it from IDE
    val workDir = Paths.get("../work")


    fun resolve(relativePath: Path): Path {
        check(! relativePath.isAbsolute)
        return workDir.resolve(relativePath)
    }
}