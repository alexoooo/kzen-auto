package tech.kzen.auto.client.objects.document.common.edit

import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash


// The debounce + flush kernel behind every editor's typing buffer, usable on its own by editors whose commit
// isn't a plain attribute write (see AttributeCommitter for the full pipeline).
//
// Debounce-race invariant: wire [flush] to BOTH onBlur and componentWillUnmount, so a pending edit is committed
// before a following separate command (e.g. a step rename) rather than racing it.
class DebouncedSubmitter(
    delayMillis: Int = defaultDelayMillis,
    private val submit: suspend () -> Unit
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val defaultDelayMillis = 1000
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The async{} must stay INSIDE the debounced lambda: lodash's flush() invokes the wrapped function
    // synchronously and util.async is startCoroutine (runs to the first suspension), which is what sequences a
    // flushed write ahead of the caller's next command. Launching the coroutine outside would let that command
    // race the flushed write.
    private val debounce: FunctionWithDebounce = lodash.debounce({
        async {
            submit()
        }
    }, delayMillis)


    //-----------------------------------------------------------------------------------------------------------------
    fun schedule() {
        debounce.apply()
    }


    fun flush() {
        debounce.flush()
    }


    fun cancel() {
        debounce.cancel()
    }
}
