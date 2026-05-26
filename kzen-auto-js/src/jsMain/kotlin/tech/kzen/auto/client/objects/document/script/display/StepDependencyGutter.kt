package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.BoxSizing
import web.cssom.Color
import web.cssom.LineStyle
import web.cssom.NamedColor
import web.cssom.Position
import web.cssom.number
import web.cssom.pct
import web.cssom.px


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


//---------------------------------------------------------------------------------------------------------------------
fun ChildrenBuilder.stepDependencyGutterCellForStep(index: Int, edges: StepDependencyEdges) {
    val hasCrossBranch = edges.crossBranchOutgoingSourceIndices.isNotEmpty() ||
        edges.crossBranchIncomingTargetIndices.isNotEmpty()

    if (edges.numLanes == 0 && !hasCrossBranch) {
        return
    }

    if (hasCrossBranch) {
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
            sourceMarker()
        }
        if (isTarget) {
            targetMarker()
        }
    }
}


fun ChildrenBuilder.stepDependencyGutterCellForBetween(stepIndexAbove: Int, edges: StepDependencyEdges) {
    val hasCrossBranch = edges.crossBranchOutgoingSourceIndices.isNotEmpty() ||
        edges.crossBranchIncomingTargetIndices.isNotEmpty()

    if (hasCrossBranch) {
        // NB: reserve phantom slot at leftmost, matching the step-row layout for body-x consistency.
        phantomMarkerLane(showSource = false, showTarget = false)
    }

    laneContainer(edges) { laneEdges ->
        val passes = laneEdges.any { it.first <= stepIndexAbove && it.last >= stepIndexAbove + 1 }
        if (passes) {
            trunkLine()
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
private fun ChildrenBuilder.laneContainer(
    edges: StepDependencyEdges,
    laneContent: ChildrenBuilder.(List<IntRange>) -> Unit
) {
    if (edges.numLanes == 0) {
        return
    }

    // NB: lane 0 (shortest spans, allocated first) sits closest to the step content; larger-span lanes extend left.
    for (laneIdx in (edges.numLanes - 1) downTo 0) {
        div {
            css {
                position = Position.relative
                width = stepDependencyLaneWidth
                flexShrink = number(0.0)
            }
            laneContent(edges.laneEdges[laneIdx])
        }
    }
}


private fun ChildrenBuilder.phantomMarkerLane(showSource: Boolean, showTarget: Boolean) {
    div {
        css {
            position = Position.relative
            width = stepDependencyLaneWidth
            flexShrink = number(0.0)
        }
        if (showSource) {
            sourceMarker()
        }
        if (showTarget) {
            targetMarker()
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


private fun ChildrenBuilder.sourceMarker() {
    // NB: anchored at the bottom of the source row so the trunk emerges from the bottom edge
    //     and the connecting line to the next-down target is minimized.
    div {
        css {
            position = Position.absolute
            bottom = 0.px
            left = 50.pct
            marginLeft = stepDependencyMarkerHalfMarginNeg
            width = stepDependencyMarkerSize
            height = stepDependencyMarkerSize
            borderRadius = 50.pct
            borderStyle = LineStyle.solid
            borderWidth = stepDependencyMarkerBorderWidth
            borderColor = stepDependencyTrunkColor
            backgroundColor = NamedColor.white
            boxSizing = BoxSizing.borderBox
        }
    }
}


private fun ChildrenBuilder.targetMarker() {
    // NB: anchored at the top of the target row, mirroring the source marker for minimum line span.
    div {
        css {
            position = Position.absolute
            top = 0.px
            left = 50.pct
            marginLeft = stepDependencyMarkerHalfMarginNeg
            width = stepDependencyMarkerSize
            height = stepDependencyMarkerSize
            borderRadius = 50.pct
            backgroundColor = stepDependencyTrunkColor
            boxSizing = BoxSizing.borderBox
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
