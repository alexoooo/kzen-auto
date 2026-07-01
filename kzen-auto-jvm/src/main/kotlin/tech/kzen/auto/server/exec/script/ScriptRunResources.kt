package tech.kzen.auto.server.exec.script


/**
 * The run-scoped resource handles a Script's steps share — the *value* side of the resource registry (the
 * engine's [tech.kzen.lib.common.exec.engine.Execution.resource] owns *disposal*; this owns the live handle a
 * later step reads). One instance per top-level Script run, inherited by every hosted child Script (a RunStep's
 * callee — see [ScriptRunContext.host]) so a browser opened in the parent is the same browser a nested Script
 * drives. Replaces the former process-wide WebDriverContext singleton, so concurrent runs no longer collide on
 * one browser.
 *
 * Single-threaded: touched only from the owning run's coroutine (and its synchronously-hosted children), never
 * concurrently, so it needs no synchronization.
 */
class ScriptRunResources {
    private val handles = HashMap<String, Any?>()


    fun put(key: String, value: Any?) {
        handles[key] = value
    }


    fun get(key: String): Any? {
        return handles[key]
    }


    fun remove(key: String) {
        handles.remove(key)
    }
}
