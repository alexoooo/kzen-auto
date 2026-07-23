package tech.kzen.auto.client.objects.document.common.edit

import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.service.store.MirroredGraphStore


// The shared attribute-edit commit pipeline: pending value → CommonEditUtils.editCommand → apply → error
// capture, then [onCommitted] on success only (a failed write changed nothing, so its consumers must not
// recompute).
//
// Every constructor argument that reads props or state is a lambda, evaluated at commit time — capturing the
// value instead would pin the first render's props for the component's whole life.
//
// [logicValidationGlobal] (optional, wired only where keystroke immediacy is wanted — currently
// KotlinExpressionEditor) reports edit-activity for this committer's document so the run cluster's "revalidating"
// indicator lights up the instant a key is pressed, before the debounce fires; this committer is the edit-pending
// token (one document has many editors). The paradigm's revalidation `inFlight` then takes over — one continuous
// busy window. The clear is INTERNAL to the committer, on every exit path, never delegated to the caller-owned
// onCommitted/onError.
class AttributeCommitter(
    private val graphStore: () -> MirroredGraphStore,
    private val objectLocation: () -> ObjectLocation,
    private val attributePath: () -> AttributePath,
    // null = nothing pending, so the debounced path commits nothing
    private val pendingNotation: () -> AttributeNotation?,
    private val onCommitted: ((AttributeNotation) -> Unit)? = null,
    // null message = success
    private val onError: ((String?) -> Unit)? = null,
    private val logicValidationGlobal: LogicValidationGlobal? = null,
    delayMillis: Int = DebouncedSubmitter.defaultDelayMillis
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val submitter = DebouncedSubmitter(delayMillis) { commitNow() }


    //-----------------------------------------------------------------------------------------------------------------
    fun schedule() {
        // Light up "revalidating" the instant a key is pressed — before the 1s debounce — then hold it through
        // the commit and the paradigm's server revalidation.
        markEditPending(true)
        submitter.schedule()
    }


    // Wire to onBlur AND componentWillUnmount (see DebouncedSubmitter's debounce-race invariant).
    fun flush() {
        submitter.flush()
    }


    fun cancel() {
        submitter.cancel()
        // cancel() discards the pending commit, so the pending mark must go with it (else busy sticks forever).
        markEditPending(false)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Debounced/flush path: reads the pending value at commit time.
    suspend fun commitNow() {
        val notation = pendingNotation()
        if (notation == null) {
            // Nothing to write (the value reverted to the server value), but schedule() may have marked pending —
            // settle it here or the busy indicator would stick with no commit to clear it.
            settleEditPending()
            return
        }
        commitNow(notation)
    }


    // Immediate-submit path for event-carried values (a toggle/select choice, a reference insertion): the caller
    // passes the value explicitly because React state written in the same tick may not be readable yet.
    suspend fun commitNow(notation: AttributeNotation) {
        try {
            val command = CommonEditUtils.editCommand(objectLocation(), attributePath(), notation)
            val errorMessage = CommonEditUtils.applyCommand(graphStore(), command)

            onError?.invoke(errorMessage)

            if (errorMessage == null) {
                onCommitted?.invoke(notation)
            }
        }
        finally {
            // Settle on EVERY exit (success AND error): the commit's own notation change has already re-armed the
            // paradigm's revalidation inFlight synchronously, so busy stays continuous across this hand-off.
            settleEditPending()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Clears the edit-pending mark unless another debounced edit is already scheduled (a keystroke that landed
    // while this commit was in flight re-arms the debounce; that edit's own commit settles it) — clearing
    // unconditionally would drop the busy indicator in the gap between this commit's revalidation settling and
    // the follow-up debounce firing.
    private fun settleEditPending() {
        if (!submitter.pending()) {
            markEditPending(false)
        }
    }


    private fun markEditPending(pending: Boolean) {
        val global = logicValidationGlobal
            ?: return
        global.editActivity(objectLocation().documentPath, this, pending)
    }
}
