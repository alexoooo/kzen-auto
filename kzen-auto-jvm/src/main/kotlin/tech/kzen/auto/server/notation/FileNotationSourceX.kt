package tech.kzen.auto.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.read.flat.source.NotationSource
import java.nio.file.Files
import java.nio.file.Paths


class FileNotationSourceX : NotationSource {
    override suspend fun read(location: ProjectPath): ByteArray {
        val path = Paths.get(location.relativeLocation)
        return Files.readAllBytes(path)
    }
}