package tech.kzen.auto.server.exec.script.test

import java.util.concurrent.ConcurrentHashMap


/**
 * Process-global sink the test-only [OpenResourceTestStep] records disposals into (the closer registered with
 * the engine has no other channel back to the test). [ScriptExtensibilityTest][tech.kzen.auto.server.exec.script.ScriptExtensibilityTest]
 * [reset]s it before each run and asserts [disposed] afterward to check the [ClosePolicy][tech.kzen.lib.common.exec.logic.ResourceClosePolicy]
 * teardown. Thread-safe because the engine fires closers on its own coroutine.
 */
object ResourceDisposalLog {
    private val disposedKeys = ConcurrentHashMap.newKeySet<String>()


    fun reset() {
        disposedKeys.clear()
    }


    fun record(key: String) {
        disposedKeys.add(key)
    }


    fun disposed(): Set<String> {
        return disposedKeys.toSet()
    }
}
