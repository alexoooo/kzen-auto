package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.lib.common.model.location.ObjectLocation


// Per-step transient UI state. Held under ScriptState.steps, keyed by ObjectLocation — the one
// keyed-map sub-state in the script model, justified by the dynamic step count (unlike Report's
// fixed sections). NOT progress/validation: those are distinct server-backed network calls kept
// beside this so the client stays aligned with the server.
//
// hoveredScreenshot is set only for a RunStep: the sub-script step whose frame its right-of-step
// floating preview should show while a strip thumbnail is hovered (null = show the latest frame).
data class ScriptStepState(
    val expanded: Boolean = false,
    val hoveredScreenshot: ObjectLocation? = null
)
