package tech.kzen.auto.util

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime


object WorkUtils {
    val processSignature = LocalDateTime.now().toString()

    // NB: sibling to hide it from IDE
    private val workDir = Paths.get("../work")


    fun resolve(relativePath: Path): Path {
        check(! relativePath.isAbsolute)
        return workDir.resolve(relativePath)
    }
}