package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
private val stepDependencyLaneWidth = 14.px
private val stepDependencyTrunkColor = Color("rgb(120, 144, 156)")
private val stepDependencyTrunkLineWidth = 2.px
private val stepDependencyMarkerSize = 10.px
private val stepDependencyMarkerBorderWidth = 2.px


//---------------------------------------------------------------------------------------------------------------------
fun ChildrenBuilder.stepDependencyGutterCellForStep(index: Int, edges: StepDependencyEdges) {
    laneContainer(edges) { laneEdges ->
        val cover = laneEdges.firstOrNull { index in it }
            ?: return@laneContainer

        val isSource = index == cover.first
        val isTarget = index == cover.last

        trunkLine(fromTop = !isSource, toBottom = !isTarget)

        if (isSource) {
            sourceMarker()
        }
        if (isTarget) {
            targetMarker()
        }
    }
}


fun ChildrenBuilder.stepDependencyGutterCellForBetween(stepIndexAbove: Int, edges: StepDependencyEdges) {
    laneContainer(edges) { laneEdges ->
        val passes = laneEdges.any { it.first <= stepIndexAbove && it.last >= stepIndexAbove + 1 }
        if (passes) {
            trunkLine(fromTop = true, toBottom = true)
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

    div {
        css {
            display = Display.flex
            flexShrink = number(0.0)
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
}


private fun ChildrenBuilder.trunkLine(fromTop: Boolean, toBottom: Boolean) {
    div {
        css {
            position = Position.absolute
            left = 50.pct
            marginLeft = (-1).px
            width = stepDependencyTrunkLineWidth
            backgroundColor = stepDependencyTrunkColor

            if (fromTop) {
                top = 0.px
            }
            else {
                top = 50.pct
            }

            if (toBottom) {
                bottom = 0.px
            }
            else {
                height = 50.pct
            }
        }
    }
}


private fun ChildrenBuilder.sourceMarker() {
    div {
        css {
            position = Position.absolute
            top = 50.pct
            left = 50.pct
            width = stepDependencyMarkerSize
            height = stepDependencyMarkerSize
            marginTop = (-5).px
            marginLeft = (-5).px
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
    div {
        css {
            position = Position.absolute
            top = 50.pct
            left = 50.pct
            width = stepDependencyMarkerSize
            height = stepDependencyMarkerSize
            marginTop = (-5).px
            marginLeft = (-5).px
            borderRadius = 50.pct
            backgroundColor = stepDependencyTrunkColor
            boxSizing = BoxSizing.borderBox
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
data class StepDependencyEdges(
    val laneEdges: List<List<IntRange>>
) {
    val numLanes: Int get() = laneEdges.size


    companion object {
        val EMPTY = StepDependencyEdges(emptyList())


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

            val indexByIdentifier: Map<String, Int> = stepLocations
                .withIndex()
                .mapNotNull { (i, loc) ->
                    val name = loc.objectPath.name.value
                    if (isValidIdentifier(name)) name to i else null
                }
                .toMap()

            val coalesce = graphDefinitionAttempt.graphStructure.graphNotation.coalesce
            val rawEdges = mutableSetOf<Pair<Int, Int>>()

            for ((targetIndex, targetLocation) in stepLocations.withIndex()) {
                val host = ObjectReferenceHost.ofLocation(targetLocation)

                val objectDefinition = graphDefinitionAttempt.objectDefinitions[targetLocation]
                if (objectDefinition != null) {
                    for ((_, definitionReference) in objectDefinition.attributeReferencesIncludingWeak()) {
                        val resolved = coalesce.locateOptional(definitionReference.objectReference, host)
                            ?: continue
                        val sourceIndex = indexByLocation[resolved]
                            ?: continue
                        if (sourceIndex < targetIndex) {
                            rawEdges.add(sourceIndex to targetIndex)
                        }
                    }
                }

                // NB: scan scalar strings for prior-sibling identifier matches (catches code-attribute refs like
                //     FormulaStep.code). Accepted trade-off: a non-code string value containing a prior step's name
                //     as a word produces a false edge.
                if (indexByIdentifier.isNotEmpty()) {
                    val objectNotation = coalesce[targetLocation]
                    if (objectNotation != null) {
                        forEachScalarString(objectNotation) { stringValue ->
                            for ((identifier, sourceIndex) in indexByIdentifier) {
                                if (sourceIndex < targetIndex && containsWord(stringValue, identifier)) {
                                    rawEdges.add(sourceIndex to targetIndex)
                                }
                            }
                        }
                    }
                }
            }

            if (rawEdges.isEmpty()) {
                return EMPTY
            }

            return StepDependencyEdges(packIntoLanes(rawEdges))
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


private fun forEachScalarString(notation: ObjectNotation, action: (String) -> Unit) {
    for ((_, attributeNotation) in notation.attributes.map) {
        walkAttribute(attributeNotation, action)
    }
}


private fun walkAttribute(attributeNotation: AttributeNotation, action: (String) -> Unit) {
    when (attributeNotation) {
        is ScalarAttributeNotation -> action(attributeNotation.value)
        is ListAttributeNotation -> attributeNotation.values.forEach { walkAttribute(it, action) }
        is MapAttributeNotation -> attributeNotation.map.values.forEach { walkAttribute(it, action) }
    }
}
