package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.lib.common.model.definition.AttributeDefinition
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.definition.ListAttributeDefinition
import tech.kzen.lib.common.model.definition.MapAttributeDefinition
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
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


//---------------------------------------------------------------------------------------------------------------------
fun ChildrenBuilder.stepDependencyGutterCellForStep(index: Int, edges: StepDependencyEdges) {
    val hasCrossBranch = edges.crossBranchOutgoingSourceIndices.isNotEmpty() ||
        edges.crossBranchIncomingTargetIndices.isNotEmpty()

    if (edges.numLanes == 0 && ! hasCrossBranch) {
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
        if (! isSource && ! isTarget) {
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
            marginLeft = (-1).px
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
            marginLeft = (-5).px
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
            marginLeft = (-5).px
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
            graphDefinitionAttempt: GraphDefinitionAttempt
        ): StepDependencyEdges {
            if (stepLocations.isEmpty()) {
                return EMPTY
            }

            val indexByLocation: Map<ObjectLocation, Int> = stepLocations
                .withIndex()
                .associate { (i, loc) -> loc to i }

            val coalesce = graphDefinitionAttempt.graphStructure.graphNotation.coalesce
            val scriptDocumentPath = stepLocations.first().documentPath

            val locationByIdentifier: Map<String, ObjectLocation> = coalesce.map.keys
                .asSequence()
                .filter { it.documentPath == scriptDocumentPath }
                .mapNotNull { location ->
                    val name = location.objectPath.name.value
                    if (isValidIdentifier(name)) name to location else null
                }
                .toMap()

            val rawEdges = mutableSetOf<Pair<Int, Int>>()
            val crossBranchOutgoingSourceIndices = mutableSetOf<Int>()
            val crossBranchIncomingTargetIndices = mutableSetOf<Int>()

            val documentLocations: List<ObjectLocation> = coalesce.map.keys
                .filter { it.documentPath == scriptDocumentPath }

            for (targetLocation in documentLocations) {
                val objectDefinition = graphDefinitionAttempt.objectDefinitions[targetLocation]
                    ?: continue
                val host = ObjectReferenceHost.ofLocation(targetLocation)

                fun classifyEdge(sourceLocation: ObjectLocation) {
                    if (sourceLocation == targetLocation ||
                        sourceLocation.documentPath != scriptDocumentPath) {
                        return
                    }
                    // NB: structural containment (e.g. IfStep.then[*] → its child steps) is not a data dep;
                    //     filter both directions so neither phantom markers nor in-branch edges spuriously fire.
                    if (sourceLocation.objectPath.startsWith(targetLocation.objectPath) ||
                        targetLocation.objectPath.startsWith(sourceLocation.objectPath)) {
                        return
                    }

                    val sourceIdx = indexByLocation[sourceLocation]
                    val targetIdx = indexByLocation[targetLocation]
                    when {
                        sourceIdx != null && targetIdx != null -> {
                            if (sourceIdx < targetIdx) {
                                rawEdges.add(sourceIdx to targetIdx)
                            }
                        }
                        sourceIdx != null && targetIdx == null ->
                            crossBranchOutgoingSourceIndices.add(sourceIdx)
                        sourceIdx == null && targetIdx != null ->
                            crossBranchIncomingTargetIndices.add(targetIdx)
                    }
                }

                for ((_, definitionReference) in objectDefinition.attributeReferencesIncludingWeak()) {
                    val resolved = coalesce.locateOptional(definitionReference.objectReference, host)
                        ?: continue
                    classifyEdge(resolved)
                }

                // NB: scan only value-typed scalar strings (catches code-attribute refs like FormulaStep.code).
                //     Reference-typed subtrees are skipped to avoid matching identifier paths like
                //     "main.steps/If.then/Formula 3" as the word "Formula".
                val objectNotation = coalesce[targetLocation]
                if (objectNotation != null) {
                    forEachValueScalar(objectDefinition, objectNotation) { stringValue ->
                        for ((identifier, sourceLocation) in locationByIdentifier) {
                            if (sourceLocation == targetLocation) {
                                continue
                            }
                            if (! containsWord(stringValue, identifier)) {
                                continue
                            }
                            classifyEdge(sourceLocation)
                        }
                    }
                }
            }

            if (rawEdges.isEmpty() &&
                crossBranchOutgoingSourceIndices.isEmpty() &&
                crossBranchIncomingTargetIndices.isEmpty()) {
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


//---------------------------------------------------------------------------------------------------------------------
private val stepIdentifierNameRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")


private fun isValidIdentifier(name: String): Boolean {
    return stepIdentifierNameRegex.matches(name)
}


private fun containsWord(haystack: String, needle: String): Boolean {
    return Regex("\\b" + Regex.escape(needle) + "\\b").containsMatchIn(haystack)
}


private fun forEachValueScalar(
    objectDefinition: ObjectDefinition,
    objectNotation: ObjectNotation,
    action: (String) -> Unit
) {
    for ((name, attributeDefinition) in objectDefinition.attributeDefinitions.map) {
        val attributeNotation = objectNotation.attributes.map[name]
            ?: continue
        walkValueScalar(attributeDefinition, attributeNotation, action)
    }
}


private fun walkValueScalar(
    attributeDefinition: AttributeDefinition,
    attributeNotation: AttributeNotation,
    action: (String) -> Unit
) {
    when (attributeDefinition) {
        is ReferenceAttributeDefinition ->
            return

        is ValueAttributeDefinition -> {
            if (attributeNotation is ScalarAttributeNotation) {
                action(attributeNotation.value)
            }
        }

        is ListAttributeDefinition -> {
            if (attributeNotation is ListAttributeNotation) {
                val children = attributeDefinition.values
                attributeNotation.values.forEachIndexed { i, childNotation ->
                    val childDef = children.getOrNull(i)
                        ?: return@forEachIndexed
                    walkValueScalar(childDef, childNotation, action)
                }
            }
        }

        is MapAttributeDefinition -> {
            if (attributeNotation is MapAttributeNotation) {
                for ((segment, childNotation) in attributeNotation.map) {
                    val childDef = attributeDefinition.map[segment.asKey()]
                        ?: continue
                    walkValueScalar(childDef, childNotation, action)
                }
            }
        }
    }
}
