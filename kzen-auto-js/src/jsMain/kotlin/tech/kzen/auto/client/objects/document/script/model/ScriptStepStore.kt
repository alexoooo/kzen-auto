package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.lib.common.model.location.ObjectLocation


// Thin sub-store for per-step UI state, alongside ScriptProgressStore / ScriptValidationStore. Unlike
// those, it owns no network refresh — expansion is a pure synchronous toggle — so it just writes the
// ScriptStepState slice into ScriptState; the publish drives both the step body and its sibling
// StepScreenshotPreview via the existing onScriptState channel.
class ScriptStepStore(private val store: ScriptStore) {
    fun setExpanded(objectLocation: ObjectLocation, expanded: Boolean) {
        store.update { state ->
            state.withSteps { steps ->
                // copy() preserves any future ScriptStepState fields; pruning entries equal to the
                // default keeps `steps` holding only non-default steps, so it never accumulates
                // orphans as steps are collapsed or edited away.
                val updated = (steps[objectLocation] ?: ScriptStepState()).copy(expanded = expanded)
                if (updated == ScriptStepState()) {
                    steps - objectLocation
                }
                else {
                    steps + (objectLocation to updated)
                }
            }
        }
    }
}
