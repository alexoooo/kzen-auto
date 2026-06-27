package tech.kzen.auto.client.objects.document.job

import tech.kzen.lib.common.model.location.ObjectLocation
import web.html.HTMLElement


// Singleton registry of Job card root DOM elements keyed by ObjectLocation. Each JobObjectSlot
// attaches/clears its root element via a callback ref; JobController reads element rects to map a
// drag cursor's Y onto an insertion index (card midpoints). Trimmed from StepRowRefRegistry — Job
// has no dependency overlay, so the observe/notify machinery is dropped. A process-global singleton:
// only one Job document is open at a time, and unmounting all cards naturally clears the map.
object JobCardRowRegistry {
    private val rowElements = mutableMapOf<ObjectLocation, HTMLElement>()


    fun register(location: ObjectLocation, element: HTMLElement) {
        rowElements[location] = element
    }


    fun unregister(location: ObjectLocation, element: HTMLElement) {
        // Only remove if the element matches — a stale callback from a prior render must not clear a
        // fresh registration that already replaced it.
        if (rowElements[location] === element) {
            rowElements.remove(location)
        }
    }


    fun get(location: ObjectLocation): HTMLElement? {
        return rowElements[location]
    }
}
