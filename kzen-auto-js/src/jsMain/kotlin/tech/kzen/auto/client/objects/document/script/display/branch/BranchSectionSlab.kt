package tech.kzen.auto.client.objects.document.script.display.branch

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayDefault
import tech.kzen.auto.client.objects.document.script.display.dependency.scriptGutterRowBodyInset
import web.cssom.*


// The white surfaces that divide a segmented construct into sections — an If chain's per-branch condition
// slab, and the contentless ledge opening its Else. Both open with the same full-bleed divider and carry the
// status bar down their left edge as a real border, so every section boundary in the construct reads
// identically: hairline, white, then the next stage's own seam and soft cast (see branchStageChrome).
//
// Deliberately no boxShadow and no rounded corners: these sit mid-construct, bracketed by stages, and the
// title slab's own resting shadow gives the whole construct its elevation.


// The divider's height, and so how much of a surface's top edge it takes over.
private const val branchSectionDividerHeightPx = 1
private val branchSectionDividerHeight = branchSectionDividerHeightPx.px

// The ledge's white below its divider — a solid band rather than a fade, since it stands in for a slab.
private const val branchSectionLedgeWhitePx = 4
private val branchSectionLedgeHeight = (branchSectionLedgeWhitePx + branchSectionDividerHeightPx).px

// The slab's content padding inside its status bar: the two sum to [branchRailWidth], so a slab's row and the
// branchRailWidth-indented stage rows below it share one left column.
val branchSlabContentPadding: Length = branchRailWidth - ScriptStepDisplayDefault.statusBorderWidth

// Matches DoWhileStepDisplay's While footer, so a condition reads with the same rhythm in either construct.
private val branchSectionSlabPaddingVertical = 12.px

// Where an outset marker's lane sits, measured back from the slab's padding box — which begins INSIDE the
// status bar, hence the extra bar width. One handle strip out lands it in the card's own drag-handle column:
// the nearest place outside the card that never carries a dependency trunk (the lane column beyond it is the
// enclosing branch's rightmost, where a filled dot would read as a junction with that lane's line).
private val branchSectionSlabMarkerLeft: Length =
    0.px - ScriptStepDisplayDefault.statusBorderWidth - scriptGutterRowBodyInset


// [outsetMarker]: the incoming dependency anchor for whatever this slab heads, rendered in a lane-width
// column OUTSIDE the card's left edge. Outside because ScriptDependencyOverlay paints its polylines BEHIND
// the cards: a marker on the slab itself would have the card's opaque surface — and its status bar — between
// the wire and the dot, so the wire would read as stopping short of what it connects to.
fun ChildrenBuilder.branchSectionSlab(
    accent: Color,
    outsetMarker: (ChildrenBuilder.() -> Unit)? = null,
    content: ChildrenBuilder.() -> Unit
) {
    div {
        // NB: marks this slab as a "yield zone" so an enclosing step slot's drag handle stays hidden while the
        //     cursor is over a section header — the section has a grip of its own, and two visible handles
        //     (the construct's, spanning its whole card, and the section's) make the wrong one the obvious
        //     grab. Same mechanism as data-step-slot / data-step-branch; see ScriptStepSlot's :has() rule.
        asDynamic()["data-step-section"] = ""

        css {
            // Anchors the divider and the outset marker to THIS slab.
            position = Position.relative

            backgroundColor = NamedColor.white

            borderLeftWidth = ScriptStepDisplayDefault.statusBorderWidth
            borderLeftStyle = LineStyle.solid
            borderLeftColor = accent

            paddingLeft = branchSlabContentPadding
            paddingRight = branchSlabContentPadding
            paddingTop = branchSectionSlabPaddingVertical
            paddingBottom = branchSectionSlabPaddingVertical

            // The slab is the section's hover region, so a grip inside it reveals over the whole white
            // surface, padding included. Descendant selector because the grip belongs to the header the
            // caller renders — and a slab heads exactly one, so it can match nothing else.
            "&:hover [data-drag-handle]" {
                opacity = number(1.0)
            }
        }

        branchSectionDivider()

        if (outsetMarker != null) {
            div {
                css {
                    position = Position.absolute
                    left = branchSectionSlabMarkerLeft

                    // The slab's own content top, so the marker lands on the first row's top edge — where
                    // the overlay expects it (ScriptDependencyOverlay routes the wire into the marker's
                    // vertical centre, measured from the registered element's top).
                    top = branchSectionSlabPaddingVertical

                    // Never in the way of the card's drag handle, which shares this column.
                    pointerEvents = None.none
                }

                outsetMarker()
            }
        }

        content()
    }
}


// A section boundary with nothing to head: the ledge opening an If chain's Else, which has no condition to
// carry. Solid rather than a fade, so the Else reads as beginning at a definite edge; its shadow is the
// following stage's own branchStageTopShadow, the way a condition slab's is.
fun ChildrenBuilder.branchSectionLedge(accent: Color) {
    div {
        css {
            position = Position.relative
            height = branchSectionLedgeHeight
            backgroundColor = NamedColor.white

            borderLeftWidth = ScriptStepDisplayDefault.statusBorderWidth
            borderLeftStyle = LineStyle.solid
            borderLeftColor = accent
        }

        branchSectionDivider()
    }
}


// The hairline opening a section, grouping the surface below it with the stage that follows.
//
// Full bleed: it starts one status bar LEFT of the padding box, so it runs the construct's whole width and
// cuts the status bar rather than stopping at it. A `border-top` cannot do this — it miters diagonally into
// the border-left instead of crossing it — and only a positioned child paints above the parent's own border.
//
// Its 12% black lands on the slab's white, where it reads at hairline weight; the mirror line at the far end
// of a stage (branchStageSeam) sits on the gray stage, where the same ink reads darker and needs the left-end
// ramp that this one does not.
private fun ChildrenBuilder.branchSectionDivider() {
    div {
        css {
            position = Position.absolute
            top = 0.px
            left = (-ScriptStepDisplayDefault.statusBorderWidthPx).px
            right = 0.px
            height = branchSectionDividerHeight
            backgroundColor = branchSeamColor
            pointerEvents = None.none
        }
    }
}
