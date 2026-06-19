package tech.kzen.auto.client.objects.document.script.display.dependency

import tech.kzen.lib.common.model.location.ObjectLocation
import web.html.HTMLElement


// NB: singleton registry of step-row body DOM elements keyed by ObjectLocation.
//     ScriptBranchDisplay attaches/clears refs from each step row's body div via a callback ref;
//     ScriptDependencyOverlay reads element rects to position cross-branch polylines.
//     A process-global singleton — only one Script document is open at a time, and unmounting all step
//     rows naturally clears the map.
object StepRowRefRegistry {
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
