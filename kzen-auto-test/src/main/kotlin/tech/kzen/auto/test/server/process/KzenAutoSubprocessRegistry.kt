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


    fun resourceKey(name: String): String {
        return "sut:$name"
    }


    /**
     * Close [name]'s entry only while it still holds [process] — the identity-checked form an engine resource
     * closer must use (see [tech.kzen.lib.common.exec.engine.Execution.bind]'s closer contract). Two SUTs
     * sharing one name is a real shape (a Script re-running its Start step), and [put] already closes the
     * predecessor; a name-only close from the superseded registration's closer would then tear down the
     * REPLACEMENT, which is live. Returns false when the entry is gone or has already moved on.
     */
    fun removeAndClose(name: String, process: KzenAutoProcess): Boolean {
        val entry = entries[name]
            ?: return false
        if (entry.process !== process || ! entries.remove(name, entry)) {
            return false
        }
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
