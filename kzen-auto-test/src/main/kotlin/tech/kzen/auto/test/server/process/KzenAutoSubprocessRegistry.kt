package tech.kzen.auto.test.server.process

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap


object KzenAutoSubprocessRegistry {
    data class Entry(val process: KzenAutoProcess, val tempDir: Path?)


    private val entries = ConcurrentHashMap<String, Entry>()


    fun put(name: String, process: KzenAutoProcess, tempDir: Path?) {
        val previous = entries.put(name, Entry(process, tempDir))
        if (previous != null) {
            closeQuietly(previous)
        }
    }


    fun remove(name: String): Entry? {
        return entries.remove(name)
    }


    fun resourceKey(name: String): String {
        return "sut:$name"
    }


    fun removeAndClose(name: String): Boolean {
        val entry = remove(name)
            ?: return false
        closeQuietly(entry)
        return true
    }


    fun closeAll() {
        val snapshot = entries.values.toList()
        entries.clear()
        for (entry in snapshot) {
            closeQuietly(entry)
        }
    }


    private fun closeQuietly(entry: Entry) {
        try {
            entry.process.close()
        }
        catch (ignored: Throwable) {
        }
        try {
            entry.tempDir?.let { FixtureCopier.deleteRecursively(it) }
        }
        catch (ignored: Throwable) {
        }
    }
}
