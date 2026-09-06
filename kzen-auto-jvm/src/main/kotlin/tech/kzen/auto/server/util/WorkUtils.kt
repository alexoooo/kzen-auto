package tech.kzen.auto.server.util

import com.google.common.io.BaseEncoding
import tech.kzen.lib.common.util.digest.Digest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID


/**
 * One context's work root and the signature that marks what this context (not another live one) wrote there.
 * Instance-owned: two contexts in one JVM hold two roots and two signatures, so a root claimed by one is never
 * swept or reused by the other, and two contexts created in the same instant cannot collide.
 */
class WorkUtils(
    private val base: Path,
    private val signature: String
) {
    /** A root with a signature nothing else shares; the form tests and ad hoc callers use. */
    constructor(base: Path): this(base, freshSignature(base.fileName?.toString() ?: "work"))


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        /** The standalone default: `../work`, a sibling of the module so IDEs do not index it. */
        val standaloneRoot: Path = Paths.get("../work")

        fun sibling(): WorkUtils {
            return WorkUtils(standaloneRoot, freshSignature("standalone"))
        }

        fun temporary(name: String): WorkUtils {
            return WorkUtils(kotlin.io.path.createTempDirectory(name), freshSignature(name))
        }

        /** A signature nothing else can share: the claim token plus a random component. */
        fun freshSignature(claimToken: String): String {
            return claimToken + "-" + UUID.randomUUID()
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


    /** Marks files this context owns (a running Report output's info file): another context's mark reads as dead. */
    fun signature(): String {
        return signature
    }


    fun resolve(relativePath: String): Path {
        return resolve(Path.of(relativePath))
    }


    fun resolve(relativePath: Path): Path {
        check(!relativePath.isAbsolute)
        return base.resolve(relativePath)
    }
}
