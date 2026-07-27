package tech.kzen.auto.client.objects.document.script.display.dependency

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.wrap.refCallback
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
        phantomMarkerLane(
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
        phantomMarkerLane()
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


// The cross-branch column, on its own: the leftmost lane of a gutter cell, where an edge that leaves the
// branch entirely starts or ends. Marker-only — the connecting line is ScriptDependencyOverlay's, since it
// spans rows this cell knows nothing about.
private fun ChildrenBuilder.phantomMarkerLane(
    showSource: Boolean = false,
    showTarget: Boolean = false
) {
    laneBox {
        if (showSource) {
            dependencyMarker(MarkerKind.Source)
        }
        if (showTarget) {
            dependencyMarker(MarkerKind.Target)
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * A dependency anchor standing free of any row: one lane column carrying [rowLocation]'s marker, registered in
 * [registry] so ScriptDependencyOverlay terminates that object's polyline here. For an object whose own row
 * cannot host the marker — an If chain's IfBranch, whose condition sits on the construct's opaque card, so a
 * marker in that row would have the card's surface between it and the wire (the overlay paints behind the
 * cards). The caller positions this wherever the wire should end.
 *
 * Zero height by design, so it costs no layout wherever it is placed: the overlay reads only the registered
 * element's left edge (lane centre = left + laneWidth / 2) and its top (the target marker's own top), and it is
 * the ONLY consumer of a branch's registration — ScriptExecutionMargin looks rows up by executable step path
 * and ScriptBranchDisplay by its own step list, neither of which an IfBranch is in.
 *
 * A single object outside a step list can only ever be a cross-branch endpoint (an in-branch lane needs two
 * distinct indices in one list), which is why this is the phantom column alone.
 */
fun ChildrenBuilder.stepDependencyAnchorLane(
    rowLocation: ObjectLocation,
    registry: StepRowRefRegistry?,
    showTarget: Boolean
) {
    div {
        // NB: the same column [laneBox] lays out, minus its flex sizing — this one is positioned by its
        //     caller rather than laid out in a gutter row, but the overlay's `left + laneWidth / 2` anchor
        //     math is shared, so the width has to be.
        css {
            position = Position.relative
            width = stepDependencyLaneWidth
        }

        if (registry != null) {
            // NB: React 19 invokes Cleanup on detach. The closure captures the registry instance, which is
            //     correct across a bridge swap — the instance is controller-scoped (see scriptGutterRow).
            ref = refCallback { element ->
                registry.register(rowLocation, element)
                val cleanup: () -> Unit = { registry.unregister(rowLocation, element) }
                cleanup
            }
        }

        if (showTarget) {
            dependencyMarker(MarkerKind.Target)
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
