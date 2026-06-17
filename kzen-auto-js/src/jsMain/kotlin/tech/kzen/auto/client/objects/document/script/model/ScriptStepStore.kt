package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.lib.common.model.location.ObjectLocation


// Thin sub-store for per-step UI state, alongside ScriptProgressStore / ScriptValidationStore. Unlike
// those, it owns no network refresh — expansion is a pure synchronous toggle — so it just writes the
// ScriptStepState slice into ScriptState; the publish drives both the step body and its sibling
// StepImageThumbnail via the existing onScriptState channel.
class ScriptStepStore(private val store: ScriptStore) {
    fun setExpanded(objectLocation: ObjectLocation, expanded: Boolean) {
        update(objectLocation) { it.copy(expanded = expanded) }
    }


    // hoveredScreenshot is the sub-script step whose frame the RunStep's right-of-step preview shows
    // (null on mouse-leave → revert to the latest frame). Pure synchronous toggle like setExpanded;
    // the publish drives the RunStep's sibling StepImageThumbnail via the onScriptState channel.
    fun setHoveredScreenshot(runStepLocation: ObjectLocation, subStepLocation: ObjectLocation?) {
        update(runStepLocation) { it.copy(hoveredScreenshot = subStepLocation) }
    }


    private fun update(objectLocation: ObjectLocation, updater: (ScriptStepState) -> ScriptStepState) {
        store.update { state ->
            state.withSteps { steps ->
                // copy() preserves any other ScriptStepState fields; pruning entries equal to the
                // default keeps `steps` holding only non-default steps, so it never accumulates
                // orphans as steps are collapsed, un-hovered, or edited away.
                val updated = updater(steps[objectLocation] ?: ScriptStepState())
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
