package tech.kzen.auto.server.util

import com.google.common.io.BaseEncoding
import tech.kzen.lib.common.util.digest.Digest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime


class WorkUtils(
    private val base: Path
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val processSignature = LocalDateTime.now().toString()

        // NB: sibling to hide it from IDE
        val sibling = WorkUtils(Paths.get(
            "../work"))

        fun temporary(name: String): WorkUtils {
            return WorkUtils(kotlin.io.path.createTempDirectory(name))
        }

        private val digestEncoding = BaseEncoding.base32().omitPadding().lowerCase()

        fun filenameEncodeDigest(digest: Digest): String {
            return digestEncoding.encode(digest.toByteArray())
        }


        fun recursivelyDeleteDir(dir: Path) {
            Files.walk(dir).use { stream ->
                stream
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete)
            }
        }


        /**
         * Recursive delete that fails loudly: an undeletable entry (e.g. a file locked by an open handle
         * on Windows) throws, whereas [recursivelyDeleteDir] silently skips it.
         */
        fun deleteDirThrowing(dir: Path) {
            val toDelete = Files.walk(dir).use { stream ->
                stream.sorted(Comparator.reverseOrder()).toList()
            }
            for (path in toDelete) {
                Files.delete(path)
            }
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
        check(!relativePath.isAbsolute)
        return base.resolve(relativePath)
    }
}