package tech.kzen.auto.client.objects.document.common.edit

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
// Edit-activity reporting (the run cluster's keystroke-immediate "revalidating" indicator) is handled one layer
// down by the underlying DebouncedSubmitter via [editActivity].
class AttributeCommitter(
    private val graphStore: () -> MirroredGraphStore,
    private val objectLocation: () -> ObjectLocation,
    private val attributePath: () -> AttributePath,
    // null = nothing pending, so the debounced path commits nothing
    private val pendingNotation: () -> AttributeNotation?,
    private val onCommitted: ((AttributeNotation) -> Unit)? = null,
    // null message = success
    private val onError: ((String?) -> Unit)? = null,
    editActivity: () -> DocumentEditActivity?,
    delayMillis: Int = DebouncedSubmitter.defaultDelayMillis
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val submitter = DebouncedSubmitter(delayMillis, editActivity) { commitNow() }


    //-----------------------------------------------------------------------------------------------------------------
    fun schedule() {
        submitter.schedule()
    }


    // Wire to onBlur AND componentWillUnmount (see DebouncedSubmitter's debounce-race invariant).
    fun flush() {
        submitter.flush()
    }


    fun cancel() {
        submitter.cancel()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Debounced/flush path: reads the pending value at commit time.
    suspend fun commitNow() {
        val notation = pendingNotation() ?: return
        commitNow(notation)
    }


    // Immediate-submit path for event-carried values (a toggle/select choice, a reference insertion): the caller
    // passes the value explicitly because React state written in the same tick may not be readable yet.
    suspend fun commitNow(notation: AttributeNotation) {
        val command = CommonEditUtils.editCommand(objectLocation(), attributePath(), notation)
        val errorMessage = CommonEditUtils.applyCommand(graphStore(), command)

        onError?.invoke(errorMessage)

        if (errorMessage == null) {
            onCommitted?.invoke(notation)
        }
    }
}
