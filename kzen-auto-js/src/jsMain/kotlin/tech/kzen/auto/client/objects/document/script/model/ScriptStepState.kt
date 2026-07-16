package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.lib.common.exec.BinaryValue


// Per-step transient UI state. Held under ScriptState.steps, keyed by ObjectLocation — the one
// keyed-map sub-state in the script model, justified by the dynamic step count (unlike Report's
// fixed sections). NOT progress/validation: those are distinct server-backed network calls kept
// beside this so the client stays aligned with the server.
//
// hoveredScreenshot is set only for a RunStep: the screenshot a hovered detail-strip frame wants the
// RunStep's right-of-step floating preview to show (null = show the latest representative frame).
data class ScriptStepState(
    val expanded: Boolean = false,
    val hoveredScreenshot: BinaryValue? = null
)
