package tech.kzen.auto.client.service.logic

import tech.kzen.lib.common.model.document.DocumentPath


//---------------------------------------------------------------------------------------------------------------------
// Flavour-agnostic, push-based summary of a Logic document's "revalidating / invalid" state, so the run-control
// cluster can disable Run when the focused document is invalid (Script / Job / Flow / Report alike) and show a
// "revalidating…" indicator that lights up the instant a key is pressed and clears when everything settles.
//
// Per document it combines TWO orthogonal input channels into one derived LogicValidationSummary:
//  - editActivity — the shared commit pipeline's edit-pending signal (the committer layer), a SET of active
//    committer tokens (one document has many editor instances, so a boolean couldn't survive two overlapping
//    edits). This lights up before the debounce even fires.
//  - validation — the paradigm's async (re)validation state (in-flight) plus its first validation error.
//
// The split is load-bearing, not a nicety: with a single busy field the committer and the paradigm would be two
// writers to it, and last-writer-wins races follow directly (keystroke B lands mid-validation of keystroke A →
// A's validation completes → the paradigm's busy=false stomps B's still-pending edit and the spinner drops
// early). With split channels committers know nothing about validation and paradigms know nothing about
// edit-pending, so the race is structurally impossible: whenever a keystroke is uncommitted its committer token
// keeps editPending non-empty, and each commit re-arms the validation channel's in-flight before its token
// clears — one continuous busy window.
//
// The output stays at the summary level (busy + reason), NOT StepValidation / mechanism level — that is what
// makes it flavour-agnostic across heterogeneous validators. State is keyed by DocumentPath and consumers filter
// on the current path, so a stale publisher from a previously-focused document cannot leak. A paradigm that never
// publishes (e.g. a 3rd-party plugin paradigm that mixes in Logic) degrades to "unknown → runnable", same as
// today.
class LogicValidationGlobal {
    //-----------------------------------------------------------------------------------------------------------------
    data class LogicValidationSummary(
        // = editPending.isNotEmpty() || validationInFlight — drives the "revalidating…" indicator only.
        val busy: Boolean,
        // The paradigm's first validation error → disables Run. Null = valid OR unknown (a paradigm that never
        // published, or a fetch failure surfaced through the global error banner instead).
        val invalidReason: String?,
        // Whether a validation has ever SETTLED for this document (a paradigm published inFlight=false at least
        // once). Distinguishes "validated & valid" (invalidReason null AND validated) from "unknown / never
        // validated" (invalidReason null but NOT validated) — the status indicator shows a check only for the
        // former. The run-cluster gate ignores this (it blocks on invalidReason alone).
        val validated: Boolean
    ) {
        companion object {
            val unknown = LogicValidationSummary(busy = false, invalidReason = null, validated = false)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        // Carries the changed document so the consumer can filter on the current path without re-reading state.
        fun onLogicValidation(documentPath: DocumentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The two raw input channels plus the last-published summary (for the no-op guard and summaryFor), per
    // document. `published` stays in lock-step with derive() because reconcile is the only mutator path.
    private class Record {
        val editPending: MutableSet<Any> = mutableSetOf()
        var validationInFlight: Boolean = false
        var invalidReason: String? = null
        var validated: Boolean = false
        var published: LogicValidationSummary = LogicValidationSummary.unknown

        fun derive(): LogicValidationSummary =
            LogicValidationSummary(editPending.isNotEmpty() || validationInFlight, invalidReason, validated)
    }


    private val records = mutableMapOf<DocumentPath, Record>()
    private val observers = mutableSetOf<Observer>()


    //-----------------------------------------------------------------------------------------------------------------
    // No replay on observe: the summary is read on demand via summaryFor from the consumer's onClientState, so a
    // late observer picks up the current value without an initial fan-out.
    fun observe(observer: Observer) {
        observers.add(observer)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    //----------------------------------------------------------------------------------------------------------------
    // INPUT — edit-pending channel (the committer layer). `committerToken` identifies one editor's committer, so
    // two overlapping edits on the same document each hold their own slot in the set. Idempotent: a keystroke
    // re-marking an already-pending token doesn't fan out (Set.add returns false), so keystroke-frequency calls
    // are absorbed here rather than in the no-op guard downstream.
    fun editActivity(documentPath: DocumentPath, committerToken: Any, pending: Boolean) {
        val record = records[documentPath]
        if (record == null && !pending) {
            // Nothing pending to clear, and no reason to allocate a record for a no-op.
            return
        }

        val target = record ?: Record().also { records[documentPath] = it }
        val changed =
            if (pending) {
                target.editPending.add(committerToken)
            }
            else {
                target.editPending.remove(committerToken)
            }

        if (changed) {
            reconcile(documentPath, target)
        }
    }


    // INPUT — the paradigm's validation channel: async (re)validation in-flight + first validation error. When a
    // revalidation starts, pass the LAST-KNOWN reason (not null) so Run doesn't flicker-enable mid-keystroke; on
    // completion pass the freshly-computed reason.
    fun validation(documentPath: DocumentPath, inFlight: Boolean, invalidReason: String?) {
        // A settle (inFlight=false) marks the document validated. Even the very first "valid" settle carries
        // information — it moves the status indicator from unknown → valid — so, unlike a no-op edit-clear, it
        // always warrants a record.
        val target = records[documentPath] ?: Record().also { records[documentPath] = it }

        val nextValidated = target.validated || !inFlight
        if (target.validationInFlight == inFlight &&
                target.invalidReason == invalidReason &&
                target.validated == nextValidated
        ) {
            return
        }
        target.validationInFlight = inFlight
        target.invalidReason = invalidReason
        target.validated = nextValidated
        reconcile(documentPath, target)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // OUTPUT — the derived summary for one document; unknown (not busy, no reason) for a document with no record.
    fun summaryFor(documentPath: DocumentPath): LogicValidationSummary {
        return records[documentPath]?.published ?: LogicValidationSummary.unknown
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Drop entries whose document no longer exists in notation (rename orphans the old DocumentPath key). Purely
    // hygiene — consumers filter on the current path, so a stale entry can't leak — and silent: a vanished
    // document has no observer to notify.
    fun prune(livingDocuments: Set<DocumentPath>) {
        val stale = records.keys.filter { it !in livingDocuments }
        for (documentPath in stale) {
            records.remove(documentPath)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // No-op publish guard (broadcast-store rule): re-derive and fan out only when the published summary actually
    // changes, so keystroke-frequency edit-activity where busy is already true never reaches an observer.
    private fun reconcile(documentPath: DocumentPath, record: Record) {
        val next = record.derive()
        if (record.published == next) {
            return
        }
        record.published = next

        for (observer in observers) {
            observer.onLogicValidation(documentPath)
        }
    }
}
