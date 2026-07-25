package tech.kzen.auto.client.objects.document.script.step.control.foreach

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.objectLocationMarker
import tech.kzen.auto.client.objects.document.script.display.branch.branchRailWidth
import tech.kzen.auto.client.objects.document.script.display.dependency.StepDependencyEdges
import tech.kzen.auto.client.objects.document.script.display.dependency.StepRowRefRegistry
import tech.kzen.auto.client.objects.document.script.display.dependency.scriptGutterRow
import tech.kzen.auto.client.objects.document.script.display.dependency.scriptGutterRowBodyInset
import tech.kzen.auto.client.objects.document.script.display.dependency.stepDependencyGutterCellForStep
import tech.kzen.auto.client.objects.document.script.display.dependency.stepDependencyGutterWidthPx
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.script.model.ForEachProgress
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
/**
 * The ForEach's loop item, rendered at the top of the loop body as a `name: Type = value` row — the same shape
 * LogicSignatureEditor gives a Script parameter, because a loop item is the same kind of thing: a
 * [tech.kzen.auto.server.objects.script.binding.ForEachItemBinding] in the ForEach's `item` branch, an addressable
 * typed value that body steps reference by name but that is never itself executed.
 *
 * MANAGED, and structurally so rather than by a disabled flag: kzen creates the binding with the ForEach
 * (ForEachStepCommander) and owns it thereafter, so this row offers no rename, no delete, no type edit and no drag
 * handle. It is also deliberately NOT a member of the body branch's step list — ScriptBranchDisplay treats a step's
 * index in that list as canonical for drag reordering, drop-insertion and cursor hit-testing, so a synthetic row
 * spliced in there would corrupt all three. Sitting above the branch as its own row costs none of that.
 *
 * Routed through [scriptGutterRow] for two reasons beyond layout: it registers the row in [StepRowRefRegistry],
 * which is the sole contract ScriptDependencyOverlay anchors its polylines to (so referencing the item from a body
 * step draws a real dependency line down into that step), and it puts the row's content in the identical left
 * column as every step card below it.
 *
 * The row's right edge holds the iteration counter ([ForEachProgressButton]) — the loop's own progress rather than
 * the item's, so it sits apart from the `name: Type = value` cluster instead of trailing the item's value.
 */
fun ChildrenBuilder.forEachItemRow(
    itemLocation: ObjectLocation,
    itemType: String?,
    progress: ForEachProgress?,
    registry: StepRowRefRegistry?,
    edges: StepDependencyEdges
) {
    scriptGutterRow(
        rowLocation = itemLocation,
        registry = registry,
        gutter = {
            // Index 0 of a single-row list: the item's only edges leave the branch, so this renders the phantom
            // source marker the overlay's polyline emerges from.
            stepDependencyGutterCellForStep(0, edges)
        },
        body = { renderItemBody(itemLocation, itemType, progress, counterInset(edges)) })
}


/**
 * How far this row's body overhangs the ForEach card's right edge, and therefore how far the counter has to be
 * pulled back to line up with it.
 *
 * The row is laid out exactly like a nested step card — [scriptGutterRow] gives it the branch's dependency
 * gutter, then the drag-handle strip, then a fixed [ScriptController.stepWidth] body — all inside the loop
 * body's own [branchRailWidth] indent. Being the same width as a card but starting further right, it ends
 * further right by the sum of everything to its left. That overhang is correct for the row's content (the item
 * lines up with the body steps below it), but the counter is the LOOP's, not the item's, so it belongs on the
 * card's edge instead.
 */
private fun counterInset(edges: StepDependencyEdges): Length {
    return branchRailWidth
        .plus(scriptGutterRowBodyInset)
        .plus(stepDependencyGutterWidthPx(edges).px)
}


//---------------------------------------------------------------------------------------------------------------------
private fun ChildrenBuilder.renderItemBody(
    itemLocation: ObjectLocation,
    itemType: String?,
    progress: ForEachProgress?,
    counterInset: Length
) {
    div {
        // Opts the binding into stage-level "jump to object", so a definition error naming it can scroll here —
        // the same treatment a parameter row and a step card get.
        objectLocationMarker(itemLocation)

        css {
            // Clears the seam above; the body branch reserves its own 32px below, so no bottom padding here.
            paddingTop = 0.75.em
            display = Display.flex
            alignItems = AlignItems.center

            // Ends the row's content box at the card's right edge. Applied unconditionally so the text column
            // has the same width idle as running — the counter appearing must not reflow the row.
            paddingRight = counterInset
        }

        title = "Loop item — managed by the ForEach"

        // Declaration cluster, growing to push the counter to the row's right edge. An item display runs to
        // TraceDisplay.maxScriptTraceChars, so it is clipped rather than wrapped here (minWidth 0 is what lets a
        // flex item shrink below its content at all) — the untruncated value is in the journal popover anyway.
        div {
            css {
                flexGrow = number(1.0)
                minWidth = 0.px
                display = Display.flex
                alignItems = AlignItems.center
                overflow = Overflow.hidden
                whiteSpace = WhiteSpace.nowrap
            }

            span {
                css {
                    fontWeight = FontWeight.bold
                }
                +itemLocation.objectPath.name.value
            }

            // Skip void, mirroring StepHeader's type-chip rule — a "Unit" annotation conveys nothing. Absent while
            // the items collection is still deferring its own type.
            if (!itemType.isNullOrEmpty() && itemType != "Unit") {
                span {
                    css {
                        color = Color("gray")
                    }
                    +": $itemType"
                }
            }

            renderItemValue(progress?.item)
        }

        if (progress != null) {
            ForEachProgressButton::class.react {
                this.progress = progress
            }
        }
    }
}


// The current iteration's value, live as the loop runs. Hidden until there is one, so an idle loop reads as just
// the declared `name: Type`.
private fun ChildrenBuilder.renderItemValue(itemDisplay: String?) {
    if (itemDisplay == null) {
        return
    }

    span {
        css {
            marginLeft = 0.4.em
            color = Color("gray")
        }
        +"= "

        span {
            css {
                fontWeight = FontWeight.bold
                color = NamedColor.black
            }
            +itemDisplay
        }
    }
}
