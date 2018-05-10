package tech.kzen.auto.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.read.flat.source.NotationSource
import java.nio.file.Files
import java.nio.file.Paths


// TODO: make less fragile
class GradleNotationSourceX(
        private val fileNotationSource: FileNotationSourceX
) : NotationSource {
    override suspend fun read(location: ProjectPath): ByteArray {
        val moduleRoot =
                if (Files.exists(Paths.get("src"))) {
                    "."
                }
                else {
                    Files.list(Paths.get(".")).use {
                        val jvmModule = it.filter({
                            it.fileName.toString().endsWith("-jvm")
                        }).findAny()

                        if (! jvmModule.isPresent) {
                            throw IllegalStateException("No resources")
                        }

                        "${jvmModule.get()}"
                    }
                }

        val mainLocation = Paths.get(
                "$moduleRoot/src/main/resources/${location.relativeLocation}").normalize()
        if (Files.exists(mainLocation)) {
            return fileNotationSource.read(ProjectPath(mainLocation.toString()))
        }

        val testLocation = Paths.get(
                "$moduleRoot/src/test/resources/${location.relativeLocation}").normalize()
        if (Files.exists(testLocation)) {
            return fileNotationSource.read(ProjectPath(testLocation.toString()))
        }

        throw IllegalStateException("Unknown resource: ${location.relativeLocation}")
    }
}