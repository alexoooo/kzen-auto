package tech.kzen.auto.client.objects.document.script.display.branch

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayDefault
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
// Shared "recessed-stage chrome" for branch-bearing steps (If's Then/Else, DoWhile's Do, ForEach's body),
// mirroring the page-level header/sidebar casting a soft shadow onto the gray stage: the construct's white
// header slab casts a shadow down onto the stage, and the stage's left edge carries that slab's status bar
// down under a thin scope line. Every effect is paint-only (absolutely positioned bands and gradients, zero
// layout height) drawn straight onto the gray stage, so [branchRailWidth] of body indent is the only thing
// holding a branch's rows clear of them.
//
// Usage: wrap the construct's branch row(s) in a `position: relative` div, call branchStageAccentRail once
// (it spans the whole wrapper, so a two-branch construct gets one continuous rail), then per branch a
// branchStageSeam() followed by that branch's content inside branchStageTopShadow { ... }.

// Boundary line colour — matches the page seam / sidebar border.
private val seamColor = Color("rgba(0, 0, 0, 0.12)")

// Dissolves the last 1.5em of a vertical edge, for a stage that ends on the open page rather than on
// another slab's seam. Shared by the accent band and the scope line so they trail off together.
private val bottomFadeMask = "linear-gradient(to bottom, black calc(100% - 1.5em), transparent)"
    .unsafeCast<MaskImage>()

// How far a branch body's rows are held off the card's left edge. This is layout only — the rail's band and
// line are a few px of paint at the card's edge, and the rest is the breathing room between them and the
// dependency gutter, the way an editor's indent guide sits clear of the code it groups.
val branchRailWidth = 1.25.em

// The scope line's soft cast onto the stage, measured from the accent band's outer edge.
private const val scopeLineWidthPx = 8
private val scopeLineWidth = scopeLineWidthPx.px

// The rail's total painted width — accent band plus scope-line cast — and so where the stage's own surface
// begins. branchStageSeam ramps its left end in from the band's outer edge to here, complementing the scope
// line's fade over that same span so the two inks sum to a constant: the corner then carries the same weight
// as either line alone, and the seam reads as JOINING the rail rather than crossing it.
//
// Both simpler alternatives are visibly wrong, and the ramp is the only option with neither artifact:
//   - seam at full strength across the strip — it composites under the rail's translucent cast and the
//     doubled ink shows through as a dark notch breaking the rail;
//   - seam absent across the strip — that patch is then lighter than the seam beside it, and the eye reads
//     the shortfall as a light rectangular gap at the corner.
// Soft casts are a different case entirely and need no ramp — see branchStageTopShadow.
private const val railPaintWidthPx = ScriptStepDisplayDefault.statusBorderWidthPx + scopeLineWidthPx


// Crisp 1px gray seam separating a white slab above from the branch stage below. Its left end ramps in
// across the rail's cast — from the accent band's outer edge to [railPaintWidthPx] — so the seam and the
// rail meet as the construct's corner instead of one crossing the other; see [railPaintWidthPx] for why
// both a plain overlap and a plain cut read as artifacts there.
//
// Opening seams only. The mirror hairline where a stage CLOSES onto a slab below (DoWhile's While footer)
// is that slab's own `border-top`, not one of these: this element sits on the gray stage, so its 12% black
// lands on ~225 and reads far darker than the same 12% on the footer's white ~255. Matching the two ends
// structurally would visibly thicken the footer's edge — see branchStageAccentRail's bandOverhangBottomPx
// for how that end's corner is kept clean instead.
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

            // Ramp the left end in across the rail's cast, mirroring its fade-out so the two sum to a
            // constant — see [railPaintWidthPx]. Fade the right (open) end — the stage's side where step
            // cards overflow — so it doesn't terminate in a hard vertical edge that clashes with the
            // frame's rounded corners.
            maskImage = ("linear-gradient(to right," +
                    " transparent ${ScriptStepDisplayDefault.statusBorderWidthPx}px," +
                    " black ${railPaintWidthPx}px," +
                    " black calc(100% - 2.5em), transparent)")
                .unsafeCast<MaskImage>()
        }
    }
}


// The header slab's status bar continued down the stage's own left edge in [accent], with a thin vertical
// scope line riding its outer edge. The band carries the construct's white (or, once running, its run
// colour) past the seam, so a stage bracketed by white slabs reads as one card rather than several; the line
// groups the body the way an editor's indent guide groups a block. [branchRailWidth] of body indent measures
// past both.
//
// Called ONCE per construct, on the wrapper spanning all of its branches, so a two-branch construct (If)
// shows one unbroken rail through the seam that divides them.
//
// The stage's own surface therefore begins at the rail's outer edge, [railPaintWidthPx] — the span over
// which branchStageSeam ramps its left end in, so its line joins the rail at the corner instead of crossing
// it. branchStageTopShadow's soft cast starts at the band's outer edge and runs straight on over the scope
// line, so the corner carries both casts overlapping as well.
//
// [fadeBottom]: on where the stage ends on the open page (If, ForEach); off where a white footer closes it
// (DoWhile's While row), since there the rail lands on that footer's seam and fading it short would leave
// band and line hanging above a hard edge.
//
// [bandOverhangBottomPx]: extends the BAND alone that many px past the wrapper, to paint over the hairline
// of a slab closing the stage from below. A slab's `border-top` miters diagonally into its `border-left`,
// so its leftmost few px are a wedge of hairline grey cutting across the status bar — a notch in what
// should be one unbroken edge down the whole construct. Overhanging the opaque band by the hairline's own
// width covers exactly that wedge and nothing else, so the slab's edge keeps the weight it gets from
// sitting on white. Zero where no slab closes the stage.
//
// Deliberately a paint-only band and gradient line rather than a bordered box: cardRestingShadow on a box as
// wide as the indent casts on all four sides, and over a bare stage the right-hand cast has no white fill to
// hide it, so the box outlines itself and reads as a translucent rectangle, not a line.
fun ChildrenBuilder.branchStageAccentRail(
    accent: Color,
    fadeBottom: Boolean,
    bandOverhangBottomPx: Int = 0
) {
    div {
        css {
            position = Position.absolute
            left = 0.px
            top = 0.px
            bottom = (-bandOverhangBottomPx).px
            width = ScriptStepDisplayDefault.statusBorderWidth
            backgroundColor = accent
            pointerEvents = None.none

            if (fadeBottom) {
                maskImage = bottomFadeMask
            }
        }
    }

    // Scope line — the vertical counterpart of branchStageSeam: a crisp 1px gray line with a soft cast fading
    // over [scopeLineWidth] onto the stage, the "sidebar" analog. Spans the full height of the
    // `position: relative` wrapper, so it is continuous across every branch; pointer-events none so step cards
    // stay clickable.
    div {
        css {
            position = Position.absolute
            left = ScriptStepDisplayDefault.statusBorderWidth
            top = 0.px
            bottom = 0.px
            width = scopeLineWidth
            pointerEvents = None.none
            backgroundImage = linearGradient(
                90.deg,
                stop(seamColor, 0.px),                          // crisp 1px line
                stop(seamColor, 1.px),
                stop(Color("rgba(0, 0, 0, 0)"), scopeLineWidth))  // soft cast onto stage

            if (fadeBottom) {
                maskImage = bottomFadeMask
            }
        }
    }
}


// Soft down-shadow cast from the white slab above, wrapping a branch row; shows only over the gray
// stage. Paint-only (zero layout height).
//
// The shadow is a separate absolute overlay (not a background on this content-wrapping div) so its
// right edge can be faded with a mask without also clipping the overflowing step cards. It starts at the
// accent band's outer edge, since a shadow drawn over the opaque band would darken it outright — but unlike
// branchStageSeam it needs no left-end ramp across the scope line: it is a soft cast to begin with, so
// running straight on over that line just gives the inner corner two overlapping casts, the way a recessed
// corner lit from above reads. Soft casts compound into depth; only a crisp line composited under one reads
// as a break. Its 7px land in the empty space every branch body opens with (a leading row's top padding, or
// the branch's own 32px insertion-reservation strip — see firstOrLastInsertionPoint), so painting it above
// content is invisible. position:relative here just scopes the overlay to the branch top; it sets no
// z-index, so it creates no stacking context (the screenshot preview's cross-step z-ordering and the
// slot-anchored drag handle are unaffected).
fun ChildrenBuilder.branchStageTopShadow(block: ChildrenBuilder.() -> Unit) {
    div {
        css {
            position = Position.relative
        }

        div {
            css {
                position = Position.absolute
                left = ScriptStepDisplayDefault.statusBorderWidth
                right = 0.px
                top = 0.px
                height = 7.px
                pointerEvents = None.none
                backgroundImage = linearGradient(
                    stop(Color("rgba(0, 0, 0, 0.10)"), 0.px),
                    stop(Color("rgba(0, 0, 0, 0)"), 7.px))

                // Fade the right (open) end so the shadow doesn't terminate in a hard vertical edge
                // that clashes with the frame's rounded corners — matching the seam's right fade.
                maskImage = "linear-gradient(to right, black calc(100% - 2.5em), transparent)"
                    .unsafeCast<MaskImage>()
            }
        }

        block()
    }
}


// If-specific: the white "lip" at the BOTTOM of the Then branch — standing in for a white slab above the
// Else seam, so that seam reads the same as the Then seam (white above → 1px line → soft shadow below, the
// way the white condition slab sits above the Then seam).
//
// A paint-only absolute element rather than a background on the branch wrapper, so its right edge can be
// faded with a mask without also fading the (overflowing) step cards. Starts at the accent band's outer edge
// and needs no ramp across the scope line the way branchStageSeam does: it is an OPAQUE white fill, so
// nothing composites through it, and covering that line is the point — it paints above it (later in the
// positioned paint order), interrupting it for its own height, which is what makes the Else seam read under
// a white slab. Only the band is spared, so it runs through unbroken and a running If shows one continuous
// coloured edge. Sits in the bottom gap below the last Then step (no card content there).
//
// Caller must give the wrapping branch div position:relative so bottom:0 anchors to the Then
// branch's bottom (not the whole construct's).
fun ChildrenBuilder.branchStageThenLip() {
    div {
        css {
            position = Position.absolute
            left = ScriptStepDisplayDefault.statusBorderWidth
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
