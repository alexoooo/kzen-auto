package tech.kzen.auto.client.objects.document.script.model


// Per-step transient UI state (expand/collapse for now). Held under ScriptState.steps, keyed by
// ObjectLocation — the one keyed-map sub-state in the script model, justified by the dynamic step
// count (unlike Report's fixed sections). NOT progress/validation: those are distinct server-backed
// network calls kept beside this so the client stays aligned with the server.
data class ScriptStepState(
    val expanded: Boolean = false
)
