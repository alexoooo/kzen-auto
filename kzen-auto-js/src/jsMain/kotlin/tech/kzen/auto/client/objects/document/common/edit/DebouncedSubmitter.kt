package tech.kzen.auto.client.objects.document.common.edit

import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash


// The debounce + flush kernel behind every editor's typing buffer, usable on its own by editors whose commit
// isn't a plain attribute write (see AttributeCommitter for the full pipeline).
//
// Also the single place every debounced document edit reports edit-activity to the run cluster: schedule() marks
// this submitter as an active editor on the document's DocumentEditActivity (looked up per keystroke — a bridge
// isn't bound until the document mounts) so the "revalidating" indicator lights on the first keystroke, and every
// exit path settles the mark. This submitter instance IS the edit-pending token (one per editor); requiring the
// [editActivity] lookup keeps future editors from silently reintroducing the keystroke-latency gap.
//
// Debounce-race invariant: wire [flush] to BOTH onBlur and componentWillUnmount, so a pending edit is committed
// before a following separate command (e.g. a step rename) rather than racing it.
class DebouncedSubmitter(
    delayMillis: Int = defaultDelayMillis,
    private val editActivity: () -> DocumentEditActivity?,
    private val submit: suspend () -> Unit
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val defaultDelayMillis = 1000
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Bumped on every schedule(). runSubmit captures it on entry and settles the edit-pending mark only if it's
    // unchanged at commit end — a keystroke landing mid-commit re-arms the debounce and bumps this, so that edit's
    // own commit settles instead (one continuous busy window). Tracked here rather than via lodash's own timer
    // state, which isn't reliably observable from inside the flush re-entry.
    private var scheduleSequence = 0


    // Where the current mark is planted. Settles target this instance rather than re-invoking [editActivity]:
    // the bridge (and its DocumentEditActivity) can swap between mark and settle when a document switch doesn't
    // remount the editor, and a settle routed to the new document's bridge would leave the old document's token
    // stuck until prune.
    private var markedActivity: DocumentEditActivity? = null


    // The async{} must stay INSIDE the debounced lambda: lodash's flush() invokes the wrapped function
    // synchronously and util.async is startCoroutine (runs to the first suspension), which is what sequences a
    // flushed write ahead of the caller's next command. Launching the coroutine outside would let that command
    // race the flushed write.
    private val debounce: FunctionWithDebounce = lodash.debounce({
        async {
            runSubmit()
        }
    }, delayMillis)


    private suspend fun runSubmit() {
        val sequenceAtStart = scheduleSequence
        try {
            submit()
        }
        finally {
            // Settle on EVERY exit (success AND error): the commit inside submit() has already re-armed the
            // paradigm's revalidation inFlight synchronously, so busy stays continuous across this hand-off. A
            // keystroke that re-scheduled while this commit was in flight leaves the mark held — that edit's own
            // commit settles it — so clearing it here would drop the indicator in the gap.
            if (scheduleSequence == sequenceAtStart) {
                markedActivity?.mark(this, false)
                markedActivity = null
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun schedule() {
        // Light "revalidating" the instant a key is pressed — before the debounce fires. A mark planted on a
        // different activity (the bridge swapped between keystrokes) is settled first, so this submitter never
        // holds more than one outstanding token.
        val activity = editActivity()
        if (markedActivity !== activity) {
            markedActivity?.mark(this, false)
        }
        activity?.mark(this, true)
        markedActivity = activity
        scheduleSequence++
        debounce.apply()
    }


    fun flush() {
        debounce.flush()
    }


    fun cancel() {
        debounce.cancel()
        // cancel() discards the pending submit, so the pending mark must go with it (else busy sticks).
        markedActivity?.mark(this, false)
        markedActivity = null
    }
}
