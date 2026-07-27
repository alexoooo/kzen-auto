package tech.kzen.auto.client.objects.document.script.display.branch

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayDefault
import web.cssom.*


// A white slab carrying one section header of a segmented construct — an If chain's per-branch condition —
// between the recessed stages that hold each section's steps. The status bar continues down its left edge as a
// real border, so the construct shows one unbroken left edge from its title slab through every stage and slab.
//
// [stageAbove]: true where a recessed stage closes onto this slab from above. Draws the closing hairline — as
// this slab's OWN border-top, so 12% black lands on white and reads at hairline weight rather than the
// markedly darker value the same ink takes on the gray stage (see branchStageSeam) — plus the top padding
// that clears it. False where the slab continues the white surface directly above it, which already supplies
// the gap; a border-top there would also miter a grey wedge across the status bar, with no stage above whose
// band could overhang it (see branchStageAccentRail's bandOverhangBottomPx).
//
// Deliberately no boxShadow and no rounded corners: this sits mid-construct, bracketed by stages, and the
// title slab's own resting shadow gives the whole construct its elevation.


// What the stage above overhangs its accent band by, to cover the closing border's left miter.
const val branchSectionSlabSeamWidthPx = 1
private val branchSectionSlabSeamWidth = branchSectionSlabSeamWidthPx.px

// The slab's content padding inside its status bar: the two sum to [branchRailWidth], so a slab's row and the
// branchRailWidth-indented stage rows below it share one left column — which is what keeps the dependency
// gutter a single column down the whole construct.
val branchSlabContentPadding: Length = branchRailWidth - ScriptStepDisplayDefault.statusBorderWidth

// Matches DoWhileStepDisplay's While footer, so a condition reads with the same rhythm in either construct.
private val branchSectionSlabPaddingVertical = 12.px


fun ChildrenBuilder.branchSectionSlab(
    accent: Color,
    stageAbove: Boolean,
    content: ChildrenBuilder.() -> Unit
) {
    div {
        css {
            backgroundColor = NamedColor.white

            borderLeftWidth = ScriptStepDisplayDefault.statusBorderWidth
            borderLeftStyle = LineStyle.solid
            borderLeftColor = accent

            if (stageAbove) {
                borderTop = Border(branchSectionSlabSeamWidth, LineStyle.solid, branchSeamColor)
            }

            paddingLeft = branchSlabContentPadding
            paddingRight = branchSlabContentPadding
            paddingTop = if (stageAbove) branchSectionSlabPaddingVertical else 0.px
            paddingBottom = branchSectionSlabPaddingVertical
        }

        content()
    }
}
