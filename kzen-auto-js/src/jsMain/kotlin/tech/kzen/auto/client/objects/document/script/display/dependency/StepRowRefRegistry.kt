package tech.kzen.auto.client.objects.document.script.display.dependency

import tech.kzen.lib.common.model.location.ObjectLocation
import web.html.HTMLElement


// NB: registry of step-row DOM elements keyed by ObjectLocation. scriptGutterRow attaches/clears refs from
//     each row's outer div via a callback ref; ScriptDependencyOverlay, ScriptMoveToArrow and
//     ScriptBranchDisplay's drag-insertion read element rects from it.
//     One instance per mounted ScriptController (like ScriptStepDragStore), provided into the per-document
//     DocumentBridge under StepRowRefRegistryKey. The SAME instance is re-provided into the fresh bridge on a
//     same-archetype document switch — the controller isn't remounted then, so its children's mount-time
//     observe() subscriptions stay valid. React ref cleanup keeps the map tight as rows unmount.
class StepRowRefRegistry {
    private val rowElements = mutableMapOf<ObjectLocation, HTMLElement>()
    private val listeners = mutableListOf<() -> Unit>()


    fun register(location: ObjectLocation, element: HTMLElement) {
        val previous = rowElements[location]
        if (previous === element) {
            return
        }
        rowElements[location] = element
        notify()
    }


    fun unregister(location: ObjectLocation, element: HTMLElement) {
        // NB: only remove if the element matches — stale callbacks from prior renders should not clear
        //     a fresh registration that already replaced them.
        if (rowElements[location] === element) {
            rowElements.remove(location)
            notify()
        }
    }


    fun get(location: ObjectLocation): HTMLElement? {
        return rowElements[location]
    }


    fun observe(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }


    private fun notify() {
        // NB: defensive copy — listeners may register/unregister during notify.
        listeners.toList().forEach { it() }
    }
}
