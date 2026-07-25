package tech.kzen.auto.client.objects.document.script.step.control.foreach

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.objectLocationMarker
import tech.kzen.auto.client.objects.document.script.display.dependency.StepDependencyEdges
import tech.kzen.auto.client.objects.document.script.display.dependency.StepRowRefRegistry
import tech.kzen.auto.client.objects.document.script.display.dependency.scriptGutterRow
import tech.kzen.auto.client.objects.document.script.display.dependency.stepDependencyGutterCellForStep
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
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
 */
fun ChildrenBuilder.forEachItemRow(
    itemLocation: ObjectLocation,
    itemType: String?,
    itemValue: ExecutionValue?,
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
        body = { renderItemBody(itemLocation, itemType, itemValue) })
}


//---------------------------------------------------------------------------------------------------------------------
private fun ChildrenBuilder.renderItemBody(
    itemLocation: ObjectLocation,
    itemType: String?,
    itemValue: ExecutionValue?
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
        }

        title = "Loop item — managed by the ForEach"

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

        renderItemValue(itemValue)
    }
}


// The current iteration's value, live as the loop runs. Hidden until there is one, so an idle loop reads as just
// the declared `name: Type`.
private fun ChildrenBuilder.renderItemValue(itemValue: ExecutionValue?) {
    if (itemValue == null || itemValue is NullExecutionValue) {
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
            +executionValueText(itemValue)
        }
    }
}


private fun executionValueText(value: ExecutionValue): String {
    return when (value) {
        is ScalarExecutionValue -> value.get().toString()
        is ListExecutionValue -> value.values.map { it.get() }.toString()
        else -> value.toString()
    }
}
