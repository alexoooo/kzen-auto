package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.util.digest.Digest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


/**
 * Identifies the code a cached result was computed by, so an entry written by an earlier build of a reader (or a
 * replaced plugin jar) misses instead of contradicting what the running code produces for the same content.
 *
 * Code is identified by where its classes were loaded from: a jar by its path, size and modification time; a class
 * directory (a development build) by its file count and newest modification time. Each location is read once per
 * process, which is exactly the code this process runs: a directory rebuilt under a live server keeps its startup
 * fingerprint, matching the classes already loaded. A location that can't be read as a file (e.g. a jar nested in a
 * fat jar) gets a per-process value: never a stale hit, only a cold disk cache after a restart.
 */
object CodeFingerprint {
    private val processOnly = Digest.ofUtf8(UUID.randomUUID().toString())
    private val byLocation = ConcurrentHashMap<Path, Digest>()


    /** The host code every cached shape passes through: this server, the shared data model, kzen-lib's contract. */
    val host: Digest by lazy {
        of(listOf(CodeFingerprint::class.java, DataShape::class.java, DataContract::class.java))
    }


    /** The host plus the reader's own code, which may live in a plugin jar the host fingerprint doesn't cover. */
    fun forReader(readerClass: Class<*>): Digest =
        Digest.build {
            addDigest(host)
            addDigest(locationFingerprint(readerClass))
        }


    private fun of(classes: List<Class<*>>): Digest =
        Digest.build {
            for (type in classes) {
                addDigest(locationFingerprint(type))
            }
        }


    private fun locationFingerprint(type: Class<*>): Digest {
        val location = try {
            type.protectionDomain?.codeSource?.location?.toURI()?.let(Paths::get)
        }
        catch (_: Exception) {
            null
        }
            ?: return processOnly
        return byLocation.computeIfAbsent(location) { fingerprint(it) }
    }


    internal fun fingerprint(location: Path): Digest =
        try {
            Digest.build {
                addUtf8(location.toString())
                if (Files.isDirectory(location)) {
                    var count = 0L
                    var newest = 0L
                    Files.walk(location).use { paths ->
                        paths.filter(Files::isRegularFile).forEach {
                            count++
                            newest = maxOf(newest, Files.getLastModifiedTime(it).toMillis())
                        }
                    }
                    addLong(count)
                    addLong(newest)
                }
                else {
                    addLong(Files.size(location))
                    addLong(Files.getLastModifiedTime(location).toMillis())
                }
            }
        }
        catch (_: Exception) {
            processOnly
        }
}
