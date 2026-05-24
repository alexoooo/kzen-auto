package tech.kzen.auto.test.server.process

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.deleteRecursively
import kotlin.io.path.relativeTo
import kotlin.io.path.walk


object FixtureCopier {
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun copyToTemp(fixtureDir: Path, prefix: String = "kzen-sut-"): Path {
        require(fixtureDir.toFile().isDirectory) {
            "fixture not a directory: ${fixtureDir.toAbsolutePath()}"
        }
        val target = Files.createTempDirectory(prefix)
        for (source in fixtureDir.walk()) {
            val relative = source.relativeTo(fixtureDir).toString()
            if (relative.isEmpty()) {
                continue
            }
            val dest = target.resolve(relative)
            if (source.toFile().isDirectory) {
                Files.createDirectories(dest)
            }
            else {
                Files.createDirectories(dest.parent)
                source.copyTo(dest, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return target
    }


    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun deleteRecursively(target: Path) {
        if (target.toFile().exists()) {
            target.deleteRecursively()
        }
    }
}
