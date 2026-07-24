package tech.kzen.auto.client.objects.document

import js.objects.unsafeJso
import kotlinx.browser.window
import react.dom.html.HTMLAttributes
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import web.animations.requestAnimationFrame
import web.dom.document
import web.html.HTMLDivElement
import web.html.HTMLElement
import web.scroll.ScrollLogicalPosition
import web.scroll.nearest


//---------------------------------------------------------------------------------------------------------------------
private const val objectLocationAttribute = "data-object-location"


// Opts an object's card into stage-level location. Mark the element that visually represents the object — it is
// both the scroll target and what gets outlined — not an enclosing full-width row.
fun HTMLAttributes<HTMLDivElement>.objectLocationMarker(objectLocation: ObjectLocation) {
    asDynamic()[objectLocationAttribute] = objectLocation.asString()
}


//---------------------------------------------------------------------------------------------------------------------
// Brings an arbitrary object into view on the stage — navigating to its document first when it lives elsewhere —
// and outlines it briefly so the eye lands on it. Owned by StageController, so any stage affordance that names an
// ObjectLocation (StageErrorIndicator's error lines) can jump to it.
//
// The paradigm contract is the DOM marker above: the stage resolves its target by reading the document, and so
// never learns which paradigm is mounted; one that marks nothing degrades to plain document navigation. A marker
// rather than the existing element registries — StepRowRefRegistry lives behind the Script document's
// DocumentBridge, and JobCardRowRegistry is Job's own — because neither is reachable from the stage, and
// consulting both would put the paradigm branch this avoids right back into it.
class StageObjectLocator(
    private val navigationGlobal: NavigationGlobal
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Frames the target is given to appear. It can only land after the hash change publishes, StageController's
        // one-frame transition blank passes, the document controller mounts and its first client state arrives —
        // roughly 3s at 60Hz, beyond which the object simply has no marker (an unmarked paradigm, or an object
        // that isn't rendered as a card) and the chain stops.
        private const val resolveFrameBudget = 180

        // ScriptBranchDisplay restores the stage pane's scrollTop — synchronously and again from a
        // requestAnimationFrame — whenever its row count changes, which includes the empty -> populated transition
        // of a freshly mounted document. Its frame callback is scheduled during commit, so it runs after the
        // resolve frame that scrolled; re-applying over the following frames has the last word. Scrolling to a
        // target that is already in view is a no-op, so the extra frames cost nothing.
        private const val holdFrames = 4

        private const val pulseOutline = "2px solid #d84315"
        private const val pulseOutlineOffset = "-2px"
        private const val pulseHoldMillis = 1200
        private const val pulseFadeMillis = 400
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Bumped by every locate and by cancel. Deferred work — frame callbacks and the pulse timeouts — captures the
    // value and does nothing once it's stale, so superseding a jump needs no per-handle bookkeeping (the deferred
    // close in SidebarCreateSubMenu guards the same way).
    private var generation = 0

    private var pulsedElement: HTMLElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    // No disposal counterpart: the frame chain stops on its own budget, the pulse timeouts are generation-guarded,
    // and an outline left on a detached element is invisible — so an unmount hook would change nothing.
    fun locate(objectLocation: ObjectLocation, openDocumentPath: DocumentPath?) {
        supersedePrevious()

        if (objectLocation.documentPath != openDocumentPath) {
            // Empty params rather than goto(path)'s carry-over: the open document's parameters (a Target's
            // section, say) mean nothing in the document being jumped to, and would then ride along in every
            // sidebar href built from the live parameters.
            navigationGlobal.goto(objectLocation.documentPath, RequestParams.empty)
        }

        awaitElement(objectLocation, generation, resolveFrameBudget)
    }


    private fun supersedePrevious() {
        generation += 1
        clearPulse()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun awaitElement(objectLocation: ObjectLocation, locateGeneration: Int, framesRemaining: Int) {
        val element = findElement(objectLocation)

        if (element == null) {
            if (framesRemaining > 0) {
                requestAnimationFrame {
                    if (locateGeneration == generation) {
                        awaitElement(objectLocation, locateGeneration, framesRemaining - 1)
                    }
                }
            }
            return
        }

        scrollIntoView(element)
        pulse(element, locateGeneration)
        holdScroll(element, locateGeneration, holdFrames)
    }


    private fun holdScroll(element: HTMLElement, locateGeneration: Int, framesRemaining: Int) {
        if (framesRemaining <= 0) {
            return
        }

        requestAnimationFrame {
            if (locateGeneration != generation) {
                return@requestAnimationFrame
            }

            scrollIntoView(element)
            holdScroll(element, locateGeneration, framesRemaining - 1)
        }
    }


    private fun findElement(objectLocation: ObjectLocation): HTMLElement? {
        // Compared as an attribute value rather than matched by an attribute selector: object names are
        // user-authored, so a quote in one would break out of the selector's own quoting.
        val asString = objectLocation.asString()
        val candidates = document.querySelectorAll("[$objectLocationAttribute]")

        for (index in 0 until candidates.length) {
            val candidate = candidates[index]
            if (candidate.getAttribute(objectLocationAttribute) == asString) {
                return candidate as HTMLElement
            }
        }

        return null
    }


    private fun scrollIntoView(element: HTMLElement) {
        // Nearest leaves an already-visible card alone — the common case, an error in the open document — so a
        // click never shifts the stage out from under the user; the outline is what marks the card.
        element.scrollIntoView(unsafeJso { block = ScrollLogicalPosition.nearest })
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Inline style rather than a React-driven flag: the marked element belongs to whichever paradigm rendered it,
    // and threading a "located" prop into each one is exactly the coupling the DOM marker avoids. Safe because
    // marked cards style themselves through emotion class names and carry no React `style` prop, so a re-render
    // never rewrites them. Outline rather than border, so framing the card can't shift the layout.
    private fun pulse(element: HTMLElement, locateGeneration: Int) {
        pulsedElement = element

        element.style.outline = pulseOutline
        element.style.outlineOffset = pulseOutlineOffset
        element.style.transition = "outline-color ${pulseFadeMillis}ms ease-out"

        window.setTimeout({
            if (locateGeneration == generation) {
                element.style.outlineColor = "transparent"
            }
        }, pulseHoldMillis)

        window.setTimeout({
            if (locateGeneration == generation) {
                clearPulse()
            }
        }, pulseHoldMillis + pulseFadeMillis)
    }


    private fun clearPulse() {
        val element = pulsedElement
            ?: return
        pulsedElement = null

        element.style.outline = ""
        element.style.outlineColor = ""
        element.style.outlineOffset = ""
        element.style.transition = ""
    }
}
