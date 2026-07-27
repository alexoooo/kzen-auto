package tech.kzen.auto.client.objects.document.script.display.dependency

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
const val stepDependencyLaneWidthPx = 14
const val stepDependencyTrunkLineWidthPx = 2
const val stepDependencyMarkerSizePx = 10

val stepDependencyLaneWidth = stepDependencyLaneWidthPx.px
val stepDependencyTrunkColor = Color("rgb(120, 144, 156)")
val stepDependencyTrunkLineWidth = stepDependencyTrunkLineWidthPx.px
private val stepDependencyMarkerSize = stepDependencyMarkerSizePx.px
private val stepDependencyMarkerBorderWidth = 2.px

private val stepDependencyTrunkLineHalfMarginNeg = (-stepDependencyTrunkLineWidthPx / 2).px
private val stepDependencyMarkerHalfMarginNeg = (-stepDependencyMarkerSizePx / 2).px

private val stepDependencyLaneHalfWidth = (stepDependencyLaneWidthPx / 2).px

// Top offset that centres a trunk-width line on a marker anchored at the lane's top edge.
private val stepDependencyTrunkTopAtMarkerCenter =
    (stepDependencyMarkerSizePx / 2 - stepDependencyTrunkLineWidthPx / 2).px


private enum class MarkerKind { Source, Target }


//---------------------------------------------------------------------------------------------------------------------
/**
 * The horizontal space a gutter cell occupies for [edges] — the lane columns [stepDependencyGutterCellForStep]
 * and [stepDependencyGutterCellForBetween] emit, both of which lay out the same set: the phantom cross-branch
 * column (when there is one) plus one per in-branch lane.
 *
 * Exposed because a gutter cell shifts everything to its right, so anything that has to line up with something
 * OUTSIDE the row (a construct's own card edge) has to account for it. Kept here, beside the cells themselves,
 * so the lane count and its width stay defined once.
 */
fun stepDependencyGutterWidthPx(edges: StepDependencyEdges): Int {
    val phantomLanes = if (edges.hasCrossBranch) 1 else 0
    return (phantomLanes + edges.numLanes) * stepDependencyLaneWidthPx
}


fun ChildrenBuilder.stepDependencyGutterCellForStep(index: Int, edges: StepDependencyEdges) {
    if (edges.numLanes == 0 && !edges.hasCrossBranch) {
        return
    }

    if (edges.hasCrossBranch) {
        // NB: phantom column is rendered FIRST so it sits at the row's leftmost gutter position.
        //     The overlay polyline endpoint is then at row.left + laneWidth/2, and the cross-branch
        //     horizontal segment terminates BEFORE reaching any in-branch lane column — preventing
        //     it from visually crossing the in-branch trunks.
        stepDependencyPhantomLane(
            showSource = index in edges.crossBranchOutgoingSourceIndices,
            showTarget = index in edges.crossBranchIncomingTargetIndices)
    }

    laneContainer(edges) { laneEdges ->
        val cover = laneEdges.firstOrNull { index in it }
            ?: return@laneContainer

        val isSource = index == cover.first
        val isTarget = index == cover.last
        // NB: trunkLine only inside pass-through rows. At endpoint rows the marker is the terminus —
        //     rendering a trunk would extend the line past the marker into the row's body area.
        if (!isSource && !isTarget) {
            trunkLine()
        }
        if (isSource) {
            dependencyMarker(MarkerKind.Source)
        }
        if (isTarget) {
            dependencyMarker(MarkerKind.Target)
        }
    }
}


fun ChildrenBuilder.stepDependencyGutterCellForBetween(stepIndexAbove: Int, edges: StepDependencyEdges) {
    if (edges.hasCrossBranch) {
        // NB: reserve phantom slot at leftmost, matching the step-row layout for body-x consistency.
        stepDependencyPhantomLane()
    }

    laneContainer(edges) { laneEdges ->
        val passes = laneEdges.any { it.first <= stepIndexAbove && it.last >= stepIndexAbove + 1 }
        if (passes) {
            trunkLine()
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
private fun ChildrenBuilder.laneBox(content: ChildrenBuilder.() -> Unit) {
    div {
        css {
            position = Position.relative
            width = stepDependencyLaneWidth
            flexShrink = number(0.0)
        }
        content()
    }
}


private fun ChildrenBuilder.laneContainer(
    edges: StepDependencyEdges,
    laneContent: ChildrenBuilder.(List<IntRange>) -> Unit
) {
    if (edges.numLanes == 0) {
        return
    }

    // NB: lane 0 (shortest spans, allocated first) sits closest to the step content; larger-span lanes extend left.
    for (laneIdx in (edges.numLanes - 1) downTo 0) {
        laneBox {
            laneContent(edges.laneEdges[laneIdx])
        }
    }
}


// The cross-branch column, on its own. A row standing for a single object (an If chain's IfBranch) can only
// ever have cross-branch edges — an in-branch lane needs two distinct indices in the row's own list — so this
// IS its whole gutter, and a caller emitting it directly can reserve the column unconditionally, keeping
// sibling rows in one left column whether or not each has an edge.
//
// [targetLeadIn]: how far left of this lane a target marker's connector reaches. ScriptDependencyOverlay
// paints its polylines BEHIND the cards, so on a row that sits on an opaque surface (an If chain's condition
// slab) the line vanishes where that surface begins and the marker reads as unconnected — this carries it the
// rest of the way in. Null on the gray stage, where the polyline arrives under its own steam.
fun ChildrenBuilder.stepDependencyPhantomLane(
    showSource: Boolean = false,
    showTarget: Boolean = false,
    targetLeadIn: Length? = null
) {
    laneBox {
        if (showSource) {
            dependencyMarker(MarkerKind.Source)
        }
        if (showTarget) {
            if (targetLeadIn != null) {
                targetMarkerLeadIn(targetLeadIn)
            }
            dependencyMarker(MarkerKind.Target)
        }
    }
}


// Ends at the target marker's centre, at the marker's own vertical centre, so it reads as the same line the
// overlay's polyline terminates with rather than a second stroke meeting it.
private fun ChildrenBuilder.targetMarkerLeadIn(leadIn: Length) {
    div {
        css {
            position = Position.absolute
            top = stepDependencyTrunkTopAtMarkerCenter
            right = 50.pct
            width = leadIn.plus(stepDependencyLaneHalfWidth)
            height = stepDependencyTrunkLineWidth
            backgroundColor = stepDependencyTrunkColor
        }
    }
}


private fun ChildrenBuilder.trunkLine() {
    div {
        css {
            position = Position.absolute
            top = 0.px
            bottom = 0.px
            left = 50.pct
            marginLeft = stepDependencyTrunkLineHalfMarginNeg
            width = stepDependencyTrunkLineWidth
            backgroundColor = stepDependencyTrunkColor
        }
    }
}


// NB: source = hollow circle anchored at row bottom (trunk emerges downward).
//     Target = filled circle anchored at row top (trunk terminates into it).
//     Anchoring at opposite edges minimizes the vertical span of the connecting line.
private fun ChildrenBuilder.dependencyMarker(kind: MarkerKind) {
    div {
        css {
            position = Position.absolute
            when (kind) {
                MarkerKind.Source -> bottom = 0.px
                MarkerKind.Target -> top = 0.px
            }
            left = 50.pct
            marginLeft = stepDependencyMarkerHalfMarginNeg
            width = stepDependencyMarkerSize
            height = stepDependencyMarkerSize
            borderRadius = 50.pct
            boxSizing = BoxSizing.borderBox
            when (kind) {
                MarkerKind.Source -> {
                    borderStyle = LineStyle.solid
                    borderWidth = stepDependencyMarkerBorderWidth
                    borderColor = stepDependencyTrunkColor
                    backgroundColor = NamedColor.white
                }
                MarkerKind.Target -> {
                    backgroundColor = stepDependencyTrunkColor
                }
            }
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
data class StepDependencyEdges(
    val laneEdges: List<List<IntRange>>,
    val crossBranchOutgoingSourceIndices: Set<Int>,
    val crossBranchIncomingTargetIndices: Set<Int>
) {
    val numLanes: Int get() = laneEdges.size

    val hasCrossBranch: Boolean
        get() = crossBranchOutgoingSourceIndices.isNotEmpty() ||
            crossBranchIncomingTargetIndices.isNotEmpty()


    companion object {
        val EMPTY = StepDependencyEdges(emptyList(), emptySet(), emptySet())


        fun compute(
            stepLocations: List<ObjectLocation>,
            analysis: ScriptDependencyAnalysis
        ): StepDependencyEdges {
            if (stepLocations.isEmpty()) {
                return EMPTY
            }

            val rawEdges = analysis.inBranchSourceTargetIndexPairs(stepLocations)
            val crossBranchOutgoingSourceIndices = analysis.crossBranchOutgoingSourceIndices(stepLocations)
            val crossBranchIncomingTargetIndices = analysis.crossBranchIncomingTargetIndices(stepLocations)

            if (rawEdges.isEmpty() &&
                crossBranchOutgoingSourceIndices.isEmpty() &&
                crossBranchIncomingTargetIndices.isEmpty()
            ) {
                return EMPTY
            }

            return StepDependencyEdges(
                packIntoLanes(rawEdges),
                crossBranchOutgoingSourceIndices,
                crossBranchIncomingTargetIndices)
        }


        // Interval-greedy lane assignment.
        // An edge fits in a lane iff the lane's previously assigned edge ends strictly before the new edge starts;
        // this guarantees that endpoint rows belong to at most one edge per lane, so source/target markers stay unambiguous.
        private fun packIntoLanes(rawEdges: Set<Pair<Int, Int>>): List<List<IntRange>> {
            val sorted = rawEdges.sortedWith(compareBy({ it.first }, { it.second }))
            val laneEnds = mutableListOf<Int>()
            val lanes = mutableListOf<MutableList<IntRange>>()

            for ((source, target) in sorted) {
                var assignedLane = -1
                for (laneIdx in laneEnds.indices) {
                    if (laneEnds[laneIdx] < source) {
                        laneEnds[laneIdx] = target
                        lanes[laneIdx].add(source..target)
                        assignedLane = laneIdx
                        break
                    }
                }
                if (assignedLane == -1) {
                    laneEnds.add(target)
                    lanes.add(mutableListOf(source..target))
                }
            }

            return lanes.map { it.toList() }
        }
    }
}
