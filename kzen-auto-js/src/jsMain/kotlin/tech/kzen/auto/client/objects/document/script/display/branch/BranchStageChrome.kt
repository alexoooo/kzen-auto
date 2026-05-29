package tech.kzen.auto.client.objects.document.script.display.branch

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import web.cssom.Color
import web.cssom.None
import web.cssom.Position
import web.cssom.deg
import web.cssom.em
import web.cssom.linearGradient
import web.cssom.px
import web.cssom.stop


//---------------------------------------------------------------------------------------------------------------------
// Shared "recessed-stage chrome" for branch-bearing steps (If's Then/Else, Mapping's Each),
// mirroring the page-level header/sidebar casting a soft shadow onto the gray stage: a crisp
// 1px gray line at each chrome→stage boundary plus a soft shadow, running both horizontally
// (under a white slab) and vertically (down the white trunk's right edge). All effects are
// paint-only gradients (zero layout height); the opaque white trunk (rendered by
// scriptBranchContainer) masks them on the left so they show only over the gray stage.
//
// Usage: wrap the branch row(s) in a `position: relative` div, then call branchStageLedge()
// once (spans the whole wrapper), and per branch a branchStageSeam() followed by the branch
// content inside branchStageTopShadow { ... }.

// Boundary line colour — matches the page seam / sidebar border.
private val seamColor = Color("rgba(0, 0, 0, 0.12)")

// Trunk outer right edge from the row's left. scriptBranchContainer's trunk is content-box:
// 3em content width + 2×0.75em horizontal padding = 4.5em (keep in sync with that width).
private val trunkOuterEdge = 4.5.em


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
        }
    }
}


// Vertical ledge: 1px gray line + soft right-cast shadow down the trunk's outer right edge,
// the "sidebar" analog. Absolutely positioned to span the full height of its `position:
// relative` parent (so it's continuous across all branches); sits in the 0.5em trunk→stage
// gap, pointer-events none so step cards stay clickable.
fun ChildrenBuilder.branchStageLedge() {
    div {
        css {
            position = Position.absolute
            left = trunkOuterEdge
            top = 0.px
            bottom = 0.px
            width = 8.px
            pointerEvents = None.none
            backgroundImage = linearGradient(
                90.deg,
                stop(seamColor, 0.px),                      // crisp 1px line
                stop(seamColor, 1.px),
                stop(Color("rgba(0, 0, 0, 0)"), 8.px))      // soft cast onto stage
        }
    }
}


// Soft down-shadow cast from the white slab above, wrapping a branch row. Masked by the white
// trunk → shows only over the gray stage; paint-only (zero layout height).
fun ChildrenBuilder.branchStageTopShadow(block: ChildrenBuilder.() -> Unit) {
    div {
        css {
            backgroundImage = linearGradient(
                stop(Color("rgba(0, 0, 0, 0.10)"), 0.px),
                stop(Color("rgba(0, 0, 0, 0)"), 7.px))
        }
        block()
    }
}
