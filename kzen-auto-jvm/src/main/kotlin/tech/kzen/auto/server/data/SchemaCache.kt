package tech.kzen.auto.server.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.util.WorkUtils
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap


/** Fingerprint-exact memory/disk cache. [peek] is memory-only and performs no filesystem operation. */
class SchemaCache(
    private val workUtils: WorkUtils
) {
    companion object {
        const val indexDirName = "index"
        private const val shapeFilename = "shape.json"
    }


    private val memory = ConcurrentHashMap<String, DataShape>()
    private val json = Json


    fun peek(key: SchemaCacheKey): DataShape? = memory[bundleKey(key)]


    fun get(key: SchemaCacheKey): DataShape? {
        val bundleKey = bundleKey(key)
        memory[bundleKey]?.let { return it }
        val shapeFile = shapeFile(bundleKey)
        if (!Files.isRegularFile(shapeFile)) {
            return null
        }
        return try {
            json.decodeFromString<DataShape>(Files.readString(shapeFile)).also {
                memory[bundleKey] = it
            }
        }
        catch (_: Exception) {
            // Cache files are disposable and may be observed after an interrupted external copy/write.
            null
        }
    }


    fun put(key: SchemaCacheKey, shape: DataShape) {
        val bundleKey = bundleKey(key)
        val bundleDir = workUtils.resolve("$indexDirName/$bundleKey")
        Files.createDirectories(bundleDir)
        val target = bundleDir.resolve(shapeFilename)
        val temporary = Files.createTempFile(bundleDir, "shape-", ".tmp")
        try {
            Files.writeString(temporary, json.encodeToString(shape))
            try {
                Files.move(
                    temporary, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
            memory[bundleKey] = shape
        }
        finally {
            Files.deleteIfExists(temporary)
        }
    }


    fun invalidate(bundleKey: String) {
        memory.remove(bundleKey)
    }


    internal fun bundleKey(key: SchemaCacheKey): String =
        WorkUtils.filenameEncodeDigest(key.digest())


    private fun shapeFile(bundleKey: String) =
        workUtils.resolve("$indexDirName/$bundleKey/$shapeFilename")
}
