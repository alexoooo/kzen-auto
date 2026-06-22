package tech.kzen.auto.client.objects.document.script.display.edit

import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import tech.kzen.lib.common.model.location.ObjectLocation


// Shared across the whole script (provided into the per-document DocumentBridge under
// ScriptStepReferenceStoreKey) so the active expression editor (KotlinExpressionEditor) and every
// ScriptBranchDisplay coordinate one "insert a prior Step as a value" pick session. Models the gesture the
// same way ScriptStepDragStore models a drag: a single active session (which editor is picking, and which
// step locations are in scope) plus an imperative onInsert callback that a canvas card click routes back
// through.
//
// The session lives here rather than in ScriptState because (a) it carries a closure (onInsert) that can't
// sit in immutable, value-compared ScriptState, and (b) it is gestural — cancelled on Escape / blur, not
// something that should survive a notation edit. Observers derive only their own slice (am I the active
// editor? is this slot in scope?) and skip setState when unchanged, so a begin/clear re-renders only the
// active editor and the in-scope step cards.
class ScriptStepReferenceStore {
    //-----------------------------------------------------------------------------------------------------------------
    data class Session(
        val editorLocation: ObjectLocation,
        val inScopeLocations: Set<ObjectLocation>)


    interface Observer {
        fun onStepReferenceChanged()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableListOf<Observer>()

    var session: Session? = null
        private set

    private var onInsert: ((ObjectLocation) -> Unit)? = null


    // Stable handler so add/removeEventListener pair up. Escape anywhere cancels the pick — a reliable global
    // end signal, mirroring the window-level dragend cancel in ScriptStepDragStore.
    private val onWindowKeyDown: (Event) -> Unit = { event ->
        if ((event as KeyboardEvent).key == "Escape") {
            clear()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun begin(
        editorLocation: ObjectLocation,
        inScopeLocations: Set<ObjectLocation>,
        onInsert: (ObjectLocation) -> Unit
    ) {
        // Remove first in case a prior session never cleared (re-entry without an intervening clear()).
        window.removeEventListener("keydown", onWindowKeyDown)
        window.addEventListener("keydown", onWindowKeyDown)
        session = Session(editorLocation, inScopeLocations)
        this.onInsert = onInsert
        publish()
    }


    // Route a chosen step (from the editor's popover OR a canvas card click) back to the active editor, then
    // end the session. Callers only ever present valid targets (the popover lists the in-scope refs; the
    // canvas overlay exists only on in-scope cards), so no membership re-check here.
    fun selectStep(stepLocation: ObjectLocation) {
        if (session == null) {
            return
        }
        val callback = onInsert
        clear()
        callback?.invoke(stepLocation)
    }


    // End the session only if it belongs to `editorLocation` (a newer editor may have taken over).
    fun end(editorLocation: ObjectLocation) {
        if (session?.editorLocation != editorLocation) {
            return
        }
        clear()
    }


    fun clear() {
        // Detach unconditionally (before the early-return) so an idle clear still tears the listener down.
        window.removeEventListener("keydown", onWindowKeyDown)
        if (session == null && onInsert == null) {
            return
        }
        session = null
        onInsert = null
        publish()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun publish() {
        for (observer in observers.toList()) {
            observer.onStepReferenceChanged()
        }
    }
}
