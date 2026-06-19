package tech.kzen.auto.client.objects.document.script.display.dependency

import kotlinx.browser.window
import org.w3c.dom.events.Event
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation


// Shared across all ScriptBranchDisplay instances of one script (via the DocumentBridge under ScriptDragStoreKey) so a drag
// begun in one branch can be dropped into another. Holds the active drag SOURCE (set on drag start) and the
// single current drop HOVER (which branch + slot the cursor is over). Centralizing the hover means only one
// branch ever shows a drop indicator — the previously-hovered branch clears as the cursor moves on, instead
// of leaving a stale marker (there is no reliable per-branch drag-leave).
//
// Observers re-render cheaply: onDragStateChanged fires on every change, but each branch derives only its own
// slice (am I the source? is the hover in me?) and skips setState when unchanged, so a hover move re-renders
// at most the two branches whose marker visibility actually changed.
class ScriptStepDragStore {
    //-----------------------------------------------------------------------------------------------------------------
    data class DragSource(
        val objectLocation: ObjectLocation,
        val branchLocation: AttributeLocation,
        val indexInBranch: Int)


    data class DropHover(
        val branchLocation: AttributeLocation,
        val insertionIndex: Int)


    interface Observer {
        fun onDragStateChanged()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableListOf<Observer>()

    var dragSource: DragSource? = null
        private set

    var dropHover: DropHover? = null
        private set


    // Stable handler so add/removeEventListener pair up. A native drag always ends with exactly one
    // `dragend` on the source element (drop, Esc-cancel, or drop-on-non-target), and it bubbles to
    // `window` even if the source row detached mid-drag — so a window-level listener guarantees the
    // hover indicator clears on cancel, where relying on the slot's React onDragEnd alone proved flaky.
    private val onWindowDragEnd: (Event) -> Unit = { clear() }


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun begin(dragSource: DragSource) {
        // Remove first in case a prior drag never cleared (re-entry without an intervening clear()).
        window.removeEventListener("dragend", onWindowDragEnd)
        window.addEventListener("dragend", onWindowDragEnd)
        this.dragSource = dragSource
        this.dropHover = null
        publish()
    }


    fun hover(dropHover: DropHover) {
        if (dragSource == null || this.dropHover == dropHover) {
            return
        }
        this.dropHover = dropHover
        publish()
    }


    fun clear() {
        // Detach unconditionally (before the early-return) so an idle clear still tears the listener down.
        window.removeEventListener("dragend", onWindowDragEnd)
        if (dragSource == null && dropHover == null) {
            return
        }
        dragSource = null
        dropHover = null
        publish()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun publish() {
        for (observer in observers.toList()) {
            observer.onDragStateChanged()
        }
    }
}
