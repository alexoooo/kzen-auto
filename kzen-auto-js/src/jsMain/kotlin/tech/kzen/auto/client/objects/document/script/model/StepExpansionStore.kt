package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.lib.common.model.location.ObjectLocation


// Per-document UI state: which Script steps are currently expanded. Lives on ScriptStore (reached via
// the DocumentBridge under ScriptStoreKey) so the step display (ScriptStepDisplayDefault — the writer)
// and its sibling StepScreenshotPreview (the reader) can coordinate. The two share only objectLocation,
// not a prop path, but both already observe this bridged store. Deliberately NOT part of ScriptState:
// expansion is transient UI, not document/trace state, so it must not ride the
// onClientState → updateIfChanged → publish flow (which rebuilds — and would clobber — it).
class StepExpansionStore {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onStepExpansionChanged(objectLocation: ObjectLocation, expanded: Boolean)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val expanded = mutableSetOf<ObjectLocation>()
    private val observers = mutableSetOf<Observer>()


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)
    }


    fun unobserve(observer: Observer) {
        // NB: lenient (no check) — previews mount/unmount freely, and a step unmounting while
        //     expanded also clears its own entry, so a double-remove is harmless.
        observers.remove(observer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isExpanded(objectLocation: ObjectLocation): Boolean {
        return objectLocation in expanded
    }


    fun setExpanded(objectLocation: ObjectLocation, expanded: Boolean) {
        val changed =
            if (expanded) {
                this.expanded.add(objectLocation)
            }
            else {
                this.expanded.remove(objectLocation)
            }

        if (!changed) {
            return
        }

        for (observer in observers.toList()) {
            observer.onStepExpansionChanged(objectLocation, expanded)
        }
    }
}
