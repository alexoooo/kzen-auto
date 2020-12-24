package tech.kzen.auto.util

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import kotlin.io.path.ExperimentalPathApi


class WorkUtils(
    private val base: Path
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val processSignature = LocalDateTime.now().toString()

        // NB: sibling to hide it from IDE
        val sibling = WorkUtils(Paths.get(
            "../work"))

        @ExperimentalPathApi
        fun temporary(name: String): WorkUtils {
            return WorkUtils(kotlin.io.path.createTempDirectory(name))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun base(): Path {
        return base
    }

    fun resolve(relativePath: String): Path {
        return resolve(Path.of(relativePath))
    }

    fun resolve(relativePath: Path): Path {
        check(! relativePath.isAbsolute)
        return base.resolve(relativePath)
    }
}