package tech.kzen.auto.common.util.data

import java.nio.file.Path
import java.nio.file.Paths


object FilePathJvm {
    fun FilePath.Companion.of(path: Path): FilePath {
        return of(path.toAbsolutePath().normalize().toString())
    }


    fun FilePath.normalize(): FilePath {
        return FilePath.of(Paths.get(location))
    }
}