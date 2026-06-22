package tech.kzen.auto.client.objects.document.script.display.dependency

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.wrap.refCallback
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*


// Shared row layout for the script's dependency column, used by both the step list (ScriptBranchDisplay)
// and the parameter list (LogicSignatureEditor). A flex row whose leftmost cells are the dependency `gutter`
// (phantom + in-branch lanes) and whose body is offset by a fixed drag-handle strip. The OUTER row element
// is registered in StepRowRefRegistry (when rowLocation != null) so ScriptDependencyOverlay can anchor
// cross-branch polylines at row.left + laneWidth/2 — the single contract that keeps a step row and a
// parameter row vertically aligned in the same column. Steps pass a trailing thumbnail; parameters pass none.
fun ChildrenBuilder.scriptGutterRow(
    rowLocation: ObjectLocation?,
    gutter: ChildrenBuilder.() -> Unit,
    body: ChildrenBuilder.() -> Unit,
    trailing: (ChildrenBuilder.() -> Unit)? = null
) {
    div {
        css {
            display = Display.flex
            alignItems = AlignItems.stretch
        }

        if (rowLocation != null) {
            // NB: ref attaches to the OUTER row (gutter + body) so the overlay computes the polyline endpoint
            //     at row.left + laneWidth/2 — the phantom column's x. React 19 invokes Cleanup on detach.
            ref = refCallback { element ->
                StepRowRefRegistry.register(rowLocation, element)
                val cleanup: () -> Unit = { StepRowRefRegistry.unregister(rowLocation, element) }
                cleanup
            }
        }

        gutter()

        div {
            css {
                width = ScriptController.stepWidth
                flexShrink = number(0.0)
                // NB: dedicated strip for the absolute-positioned drag handle (left: -1.25em off the body's
                // left edge). Without this margin the handle overlaps the rightmost dependency-gutter lane.
                marginLeft = 1.25.em
            }
            body()
        }

        trailing?.invoke(this)
    }
}
