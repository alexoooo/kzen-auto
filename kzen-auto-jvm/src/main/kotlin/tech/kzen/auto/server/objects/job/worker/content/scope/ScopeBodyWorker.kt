package tech.kzen.auto.server.objects.job.worker.content.scope

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * The scope-compatible contract of design §6.2, defined directly for the spike rather than adapted from the
 * channel-driven Worker bases (docs/plans/2026-09-16_borrowed-elements.md §3): one entry at a time,
 * synchronously; whatever the Worker derived from the entry must be gone when [onEntryEnd] returns. A body
 * Worker is a plain nested object of its scope, not a Job Worker: it has no channel ports and no drive loop.
 *
 * Live edit (CS3): the scope migrates at an entry boundary, and its body Workers are rebuilt from the edited
 * notation. What a body Worker accumulated across entries (a running total) is carried by [captureState] on
 * the outgoing instance and [loadState] on the rebuilt one, after its [onStart]; an entry-derived state never
 * exists at the boundary, so nothing live is ever detached here.
 */
interface ScopeBodyWorker {
    suspend fun onStart(control: JobControl) {}

    /** The entry (or an element derived from it by an upstream body Worker); may emit any number of elements. */
    suspend fun onElement(element: DataValue, emit: BodyEmitter, control: JobControl)

    /** Flush scope-owned buffers; entry-derived state is invalid after this returns. */
    suspend fun onEntryEnd(control: JobControl) {}

    suspend fun onComplete(emit: BodyEmitter, control: JobControl) {}

    /** The per-scope state (running totals) to carry across a live edit; null carries nothing. */
    fun captureState(): Any? = null

    /** Adopt the state the previous instance at this body location captured. */
    fun loadState(state: Any?) {}
}
