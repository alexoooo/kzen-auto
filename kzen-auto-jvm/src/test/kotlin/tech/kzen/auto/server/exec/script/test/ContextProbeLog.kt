package tech.kzen.auto.server.exec.script.test

import java.util.concurrent.ConcurrentLinkedQueue


/**
 * Process-global ordered sink the context test steps write to, so a scenario's assertions can read what each
 * step SAW rather than only what the run returned. Mirrors [ResourceDisposalLog]'s shape (reset per run by
 * the test's `runScript`), and relies on the suite's sequential execution for the same reason.
 */
object ContextProbeLog {
    private val entries = ConcurrentLinkedQueue<String>()


    fun reset() {
        entries.clear()
    }


    fun record(entry: String) {
        entries.add(entry)
    }


    fun entries(): List<String> {
        return entries.toList()
    }
}
