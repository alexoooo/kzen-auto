package tech.kzen.auto.client.objects.document.script.display.branch

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayDefault
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
// Shared "recessed-stage chrome" for branch-bearing steps (If's Then/Else, DoWhile's Do, ForEach's
// body), mirroring the page-level header/sidebar casting a soft shadow onto the gray stage: a crisp
// 1px gray line at each chrome→stage boundary plus a soft shadow, running both horizontally
// (under a white slab) and vertically (down the white trunk's right edge). All effects are
// paint-only gradients (zero layout height); the opaque white trunk (rendered by
// scriptBranchContainer) masks them on the left so they show only over the gray stage.
//
// Usage: wrap the branch row(s) in a `position: relative` div, then call branchStageLedge()
// once (spans the whole wrapper), and per branch a branchStageSeam() followed by the branch
// content inside branchStageTopShadow { ... }.
//
// TWO LEFT-EDGE TREATMENTS. If and DoWhile lay out against the labelled white trunk
// ([branchTrunkOuterEdge]), framed by branchStageBase + branchStageLedge. ForEach has no trunk at
// all — its loop item reads as a managed row in the body — so it gets branchStageRail instead: a
// bare vertical scope line, with [branchRailWidth] of indent holding the body clear of it. The
// masking note above applies only to the trunk case; the rail paints straight onto the gray stage,
// which is why it is a gradient band and not a bordered box (see branchStageRail).

// Boundary line colour — matches the page seam / sidebar border.
private val seamColor = Color("rgba(0, 0, 0, 0.12)")

// Trunk outer right edge from the row's left. scriptBranchContainer's trunk is content-box:
// 3em content width + 2×0.75em horizontal padding = 4.5em (keep in sync with that width).
val branchTrunkOuterEdge = 4.5.em

// ForEach's body indent: how far the body's rows are held off the card's left edge. This is layout
// only — branchStageRail's line is a few px of paint at x=0, and the rest is the breathing room
// between that line and the dependency gutter, the way an editor's indent guide sits clear of the
// code it groups.
val branchRailWidth = 1.25.em


// Full-width crisp 1px gray seam separating a white slab above from the branch stage below.
// Crosses the trunk on purpose so the line is continuous with the row's left margin.
//
// Deliberately a hard `height: 1px` fill for maximum sharpness. Caveat: on fractional display
// scaling (Windows 125%/150% → device-pixel ratio 1.25/1.5) a 1 *CSS* px line is 1.25–1.5
// *device* px, so depending on where its edge lands on the device grid — which depends on the
// pixel height of the slab above — a seam can anti-alias into ~1 crisp row or split across ~2
// fuzzy rows, making sibling seams look slightly different thicknesses. A soft 2px gradient
// hairline was tried to even this out but read washed-out with background bleed-through; sharp-
// but-occasionally-fuzzy is the chosen trade-off. Crisp and uniform at 100%/200% scaling.
fun ChildrenBuilder.branchStageSeam() {
    div {
        css {
            height = 1.px
            backgroundColor = seamColor

            // Fade the line out at its right (open) end — the stage's side where step cards overflow
            // — so it doesn't terminate in a hard vertical edge that clashes with the frame's rounded
            // corners. The left end stays solid where it connects into the white trunk.
            maskImage = "linear-gradient(to right, black calc(100% - 2.5em), transparent)"
                .unsafeCast<MaskImage>()
        }
    }
}


// The vertical counterpart of branchStageSeam, and the shared paint of both vertical edges below:
// a crisp 1px gray line with a soft right-cast shadow fading over 8px, the "sidebar" analog.
//
// Absolutely positioned to span the full height of its `position: relative` parent (so it's
// continuous across all branches of a construct); pointer-events none so step cards stay clickable.
// The bottom end is masked away so the line trails off instead of terminating in a square edge that
// would clash with the frame's rounded bottom corner.
private fun ChildrenBuilder.branchStageVerticalEdge(edgeLeft: Length) {
    div {
        css {
            position = Position.absolute
            left = edgeLeft
            top = 0.px
            bottom = 0.px
            width = 8.px
            pointerEvents = None.none
            backgroundImage = linearGradient(
                90.deg,
                stop(seamColor, 0.px),                      // crisp 1px line
                stop(seamColor, 1.px),
                stop(Color("rgba(0, 0, 0, 0)"), 8.px))      // soft cast onto stage

            maskImage = "linear-gradient(to bottom, black calc(100% - 1.5em), transparent)"
                .unsafeCast<MaskImage>()
        }
    }
}


// Trunk-bearing constructs (If, DoWhile): the ledge down the white trunk's outer RIGHT edge, sitting
// in the 0.5em trunk→stage gap, with the trunk's white fill on one side and the gray stage on the
// other. Paired with branchStageBase, which frames the trunk's other two edges.
fun ChildrenBuilder.branchStageLedge() {
    branchStageVerticalEdge(branchTrunkOuterEdge)
}


// Trunkless constructs (ForEach): the same edge moved to the construct's own LEFT edge, where it
// becomes a thin vertical scope line — the header card's left edge continuing past the seam to group
// the body, the way an editor's indent guide groups a block. [branchRailWidth] of body indent holds
// the rows clear of it.
//
// Deliberately NOT branchStageBase: that frames a white trunk, and its cardRestingShadow casts on
// all four sides of a box as wide as the indent — over a bare stage the right-hand cast has no white
// fill to hide it, so the box outlines itself and reads as a translucent rectangle, not a line.
fun ChildrenBuilder.branchStageRail() {
    branchStageVerticalEdge(0.px)
}


// The white "⌐" frame's two OUTER edges: a 1px hairline down the trunk's left (outer) edge and
// across its bottom, with rounded bottom corners — the seamColor hairline + a subtle soft shade,
// matching branchStageLedge (the trunk's inner/right edge) and branchStageSeam. The header above
// supplies the rounded top corners; the trunk's right edge is the ledge; the stage's right side
// stays open (nested step cards are wider than the header and overflow there).
//
// One absolute element spanning the full height of its position:relative parent, so the left
// hairline is continuous across all branches and the bottom hairline + rounded corners land at the
// very bottom (no per-branch seam). pointer-events none so step cards stay clickable. Pairs with
// the white trunk's own rounded bottom (scriptBranchContainer's roundedBottom) — the border arc
// traces the trunk's clipped white edge so the corner reads as truly rounded, not a line over a
// square fill.
//
// Trunk-only, and requires the white fill: cardRestingShadow casts on ALL four sides of this box,
// so over the trunk the right-hand cast lands on opaque white and is invisible, but on a bare
// stage it would outline the box as a translucent rectangle. Trunkless constructs use
// branchStageRail instead.
fun ChildrenBuilder.branchStageBase() {
    div {
        css {
            position = Position.absolute
            left = 0.px
            top = 0.px
            bottom = 0.px
            width = branchTrunkOuterEdge
            pointerEvents = None.none

            borderLeft = Border(1.px, LineStyle.solid, seamColor)
            borderBottom = Border(1.px, LineStyle.solid, seamColor)
            borderBottomLeftRadius = ScriptStepDisplayDefault.cardCornerRadius
            borderBottomRightRadius = ScriptStepDisplayDefault.cardCornerRadius

            // Subtle soft shade lifting the white trunk off the gray page (the app-wide resting
            // card shadow); casts outward onto the page (left/below the trunk), pairing with the
            // hairline to read as the frame's border.
            boxShadow = ScriptStepDisplayDefault.cardRestingShadow
        }
    }
}


// Soft down-shadow cast from the white slab above, wrapping a branch row; shows only over the gray
// stage. Paint-only (zero layout height).
//
// The shadow is a separate absolute overlay (not a background on this content-wrapping div) so its
// right edge can be faded with a mask without also clipping the overflowing step cards. It spans
// the STAGE only — [leftEdgeWidth] is where the stage begins, i.e. the trunk's inner edge, or 0 for
// a trunkless construct whose whole width is stage — and sits in the 32px insertion-reservation
// strip at the top of the branch (no card content there — see firstOrLastInsertionPoint), so
// painting it above content is invisible except over that empty strip. position:relative here just
// scopes the overlay to the branch top; it sets no z-index, so it creates no stacking context (the
// screenshot preview's cross-step z-ordering and the slot-anchored drag handle are unaffected).
fun ChildrenBuilder.branchStageTopShadow(
    leftEdgeWidth: Length = branchTrunkOuterEdge,
    block: ChildrenBuilder.() -> Unit
) {
    div {
        css {
            position = Position.relative
        }

        div {
            css {
                position = Position.absolute
                left = leftEdgeWidth
                right = 0.px
                top = 0.px
                height = 7.px
                pointerEvents = None.none
                backgroundImage = linearGradient(
                    stop(Color("rgba(0, 0, 0, 0.10)"), 0.px),
                    stop(Color("rgba(0, 0, 0, 0)"), 7.px))

                // Fade the right (open) end so the shadow doesn't terminate in a hard vertical edge
                // that clashes with the frame's rounded corners — matching the seam/lip right fade.
                maskImage = "linear-gradient(to right, black calc(100% - 2.5em), transparent)"
                    .unsafeCast<MaskImage>()
            }
        }

        block()
    }
}


// If-specific: the white "lip" at the BOTTOM of the Then branch — standing in for a white slab
// above the Else seam, so that seam reads the same as the Then seam (white above → 1px line → soft
// shadow below, the way the white condition slab sits above the Then seam).
//
// A paint-only absolute element rather than a background on the branch wrapper, so its right edge
// can be faded with a mask without also fading the (overflowing) step cards. It spans the STAGE
// only — starting at the trunk's inner edge — so it neither paints over the trunk's left-edge
// hairline (branchStageBase) nor needs the trunk to mask it; the white trunk continues the white
// surface on the left. Sits in the bottom gap below the last Then step (no card content there), and
// paints above branchStageLedge (later in the positioned paint order) so it interrupts the ledge
// where they cross — keeping the Else seam's white line crisp and continuous.
//
// Caller must give the wrapping branch div position:relative so bottom:0 anchors to the Then
// branch's bottom (not the whole construct's).
fun ChildrenBuilder.branchStageThenLip() {
    div {
        css {
            position = Position.absolute
            left = branchTrunkOuterEdge
            right = 0.px
            bottom = 0.px
            height = 18.px
            pointerEvents = None.none

            backgroundImage = linearGradient(
                0.deg,
                stop(NamedColor.white, 0.px),                 // white at the bottom (the Else seam)
                stop(NamedColor.white, 4.px),                 // hold solid white for a few px above the line
                stop(Color("rgba(255, 255, 255, 0)"), 18.px)) // then fade up to transparent

            // Fade the right (open) end so the white lip doesn't terminate in a hard vertical edge
            // against the gray stage, matching the seam's right-end fade.
            maskImage = "linear-gradient(to right, black calc(100% - 2.5em), transparent)"
                .unsafeCast<MaskImage>()
        }
    }
}
