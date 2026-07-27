package tech.kzen.auto.client.objects.document.script.display.dependency

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.wrap.refCallback
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*


// The body strip's fixed offset from the gutter: a dedicated lane for the absolute-positioned drag handle, which
// sits at left: -1.25em off the body's left edge and would otherwise overlap the rightmost gutter lane. Named
// because it is part of how far a row's body is pushed right of its container — see ForEachItemRow, which has to
// undo that offset to line a control up with its construct's card edge.
val scriptGutterRowBodyInset = 1.25.em


// Shared row layout for the script's dependency column, used by both the step list (ScriptBranchDisplay)
// and the parameter list (LogicSignatureEditor). A flex row whose leftmost cells are the dependency `gutter`
// (phantom + in-branch lanes) and whose body is offset by a fixed drag-handle strip. The OUTER row element
// is registered in the caller's StepRowRefRegistry (when both it and rowLocation are non-null) so
// ScriptDependencyOverlay can anchor cross-branch polylines at row.left + laneWidth/2 — the single contract
// that keeps a step row and a parameter row vertically aligned in the same column. Steps pass a trailing
// thumbnail; parameters pass none. The registry arrives as a parameter because this is a plain function, with
// no contextType slot of its own to reach the DocumentBridge through.
fun ChildrenBuilder.scriptGutterRow(
    rowLocation: ObjectLocation?,
    registry: StepRowRefRegistry?,
    gutter: ChildrenBuilder.() -> Unit,
    body: ChildrenBuilder.() -> Unit,
    trailing: (ChildrenBuilder.() -> Unit)? = null
) {
    div {
        css {
            display = Display.flex
            alignItems = AlignItems.stretch
        }

        if (rowLocation != null && registry != null) {
            // NB: ref attaches to the OUTER row (gutter + body) so the overlay computes the polyline endpoint
            //     at row.left + laneWidth/2 — the phantom column's x. React 19 invokes Cleanup on detach.
            //     The cleanup closure captures the registry instance, which is correct across a bridge swap:
            //     the instance is controller-scoped, so it outlives the bridge that carried it here.
            ref = refCallback { element ->
                registry.register(rowLocation, element)
                val cleanup: () -> Unit = { registry.unregister(rowLocation, element) }
                cleanup
            }
        }

        // Stable wrapper for the gutter so its variable lane count never shifts the body's sibling index.
        // The gutter cell emits 0..N lane boxes depending on the branch's dependency edges (zero when there
        // are none); emitting them directly into this flex row meant the body div — and the stateful step
        // slot inside it — changed position the instant an edge appeared/disappeared (e.g. inserting a step
        // reference creates the branch's first dependency), so React reconciled by index, remounted the step,
        // and the step's unmount hook collapsed its expansion (and discarded any in-progress editor buffer).
        // An always-present box keeps the body at a fixed index so React reconciles the step in place. Flex +
        // stretch reproduces the prior layout exactly (lanes laid out horizontally, full row height); it
        // collapses to zero width when empty, and the leftmost lane still sits at row.left for the overlay.
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.stretch
                flexShrink = number(0.0)
            }
            gutter()
        }

        div {
            css {
                width = ScriptController.stepWidth
                flexShrink = number(0.0)
                marginLeft = scriptGutterRowBodyInset
            }
            body()
        }

        trailing?.invoke(this)
    }
}
