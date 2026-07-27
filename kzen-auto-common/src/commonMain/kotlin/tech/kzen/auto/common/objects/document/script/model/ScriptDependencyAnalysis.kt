package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.auto.common.util.KotlinExpressionAnalyzer
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.*


data class ScriptStepDependency(
    val source: ObjectLocation,
    val target: ObjectLocation
)


data class ScriptDependencyAnalysis(
    val branchOfStep: Map<ObjectLocation, AttributeLocation>,
    val edges: Set<ScriptStepDependency>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val EMPTY = ScriptDependencyAnalysis(emptyMap(), emptySet())


        /**
         * NB: takes a [GraphDefinition] — the successful subset — rather than the [GraphDefinitionAttempt] a
         * client caller holds, because only the notation and the object definitions are read, and the server
         * (which compiles from a [GraphDefinition] and never has an attempt) needs the same analysis to decide
         * which step values are worth collecting. A client passes `attempt.successful()`, whose
         * `objectDefinitions` are the attempt's own — so the result is identical either way.
         */
        fun analyze(
            graphDefinition: GraphDefinition,
            documentPath: DocumentPath
        ): ScriptDependencyAnalysis {
            val graphNotation = graphDefinition.graphStructure.graphNotation
            val coalesce = graphNotation.coalesce
            val mainObjectLocation = documentPath.toMainObjectLocation()

            val branchOfStep = mutableMapOf<ObjectLocation, AttributeLocation>()
            walkBranch(
                AttributeLocation(mainObjectLocation, ScriptConventions.stepsAttributePath),
                graphDefinition,
                branchOfStep)

            if (branchOfStep.isEmpty()) {
                return EMPTY
            }

            // Script parameters live in the `parameters` branch (rowless bindings), never executed but
            // referenceable by name from a step's code. Walking them into branchOfStep makes a step that
            // references a parameter produce a cross-branch edge (parameter source -> step target), so the
            // dependency line is drawn just like a cross-branch step-to-step reference. Walked after the
            // steps emptiness check so a parameter-only document (no steps to reference them) still short-
            // circuits to EMPTY.
            walkBranch(
                AttributeLocation(mainObjectLocation, ScriptConventions.parametersAttributePath),
                graphDefinition,
                branchOfStep)

            // Key each in-document object by the identifier its name escapes to, so a back-ticked reference
            // (`` `my step` ``) is matched.
            //
            // Collisions are real and must OVER-report: an ObjectPath is name + nesting, so `main.steps/Foo` and
            // `main.steps/If.branches/B.steps/Foo` are distinct steps sharing one identifier, and an expression
            // naming `Foo` cannot be attributed to one from the text alone. Every candidate therefore gets it.
            // Over-reporting is the safe direction for both consumers: the client draws a surplus dependency
            // line, and [valueReferencedSteps] keeps collecting a value it might not have needed. Attributing to
            // a single winner would instead LOSE an edge — which for the value-referenced set means silently
            // eliding a value that is genuinely read.
            val locationsByIdentifierContent = coalesce.map.keys
                .asSequence()
                .filter { it.documentPath == documentPath }
                .groupBy { location ->
                    ExpressionUtils.identifierContent(
                        ExpressionUtils.escapeKotlinVariableName(location.objectPath.name.value))
                }

            val edges = mutableSetOf<ScriptStepDependency>()

            fun classifyEdge(sourceLocation: ObjectLocation, targetLocation: ObjectLocation) {
                if (sourceLocation == targetLocation || sourceLocation.documentPath != documentPath) {
                    return
                }
                // NB: structural containment (e.g. an IfBranch → its own child steps) is not a data dep.
                if (sourceLocation.objectPath.startsWith(targetLocation.objectPath) ||
                    targetLocation.objectPath.startsWith(sourceLocation.objectPath)) {
                    return
                }
                if (sourceLocation !in branchOfStep || targetLocation !in branchOfStep) {
                    return
                }
                edges.add(ScriptStepDependency(sourceLocation, targetLocation))
            }

            val documentLocations = coalesce.map.keys.filter { it.documentPath == documentPath }
            for (targetLocation in documentLocations) {
                val objectDefinition = graphDefinition.objectDefinitions[targetLocation]
                    ?: continue
                val host = ObjectReferenceHost.ofLocation(targetLocation)

                for ((_, definitionReference) in objectDefinition.attributeReferencesIncludingWeak()) {
                    val resolved = coalesce.locateOptional(definitionReference.objectReference, host)
                        ?: continue
                    classifyEdge(resolved, targetLocation)
                }

                // NB: scan only value-typed scalar strings (catches code-attribute refs like FormulaStep.code).
                //     Reference-typed subtrees are skipped to avoid matching identifier paths like
                //     "main.steps/If.branches/Branch.steps/Formula 3" as the word "Formula".
                val objectNotation = coalesce[targetLocation]
                    ?: continue
                walkValueScalars(objectDefinition, objectNotation) { stringValue ->
                    // Lexer-derived references: respects strings/comments/back-ticks and skips member selectors
                    // (see KotlinExpressionAnalyzer).
                    for (referencedIdentifier in KotlinExpressionAnalyzer.referencedIdentifiers(stringValue)) {
                        val sourceLocations = locationsByIdentifierContent[referencedIdentifier]
                            ?: continue
                        for (sourceLocation in sourceLocations) {
                            if (sourceLocation == targetLocation) {
                                continue
                            }
                            classifyEdge(sourceLocation, targetLocation)
                        }
                    }
                }
            }

            return ScriptDependencyAnalysis(branchOfStep, edges)
        }


        private fun walkBranch(
            branchAttributeLocation: AttributeLocation,
            graphDefinition: GraphDefinition,
            branchOfStep: MutableMap<ObjectLocation, AttributeLocation>
        ) {
            // Steps are the objects nested under this branch attribute, in document order.
            val graphNotation = graphDefinition.graphStructure.graphNotation
            val steps = ScriptConventions.orderedDirectChildLocations(
                graphNotation, branchAttributeLocation)

            for (step in steps) {
                branchOfStep[step] = branchAttributeLocation
            }
            for (step in steps) {
                // Recursion is metadata-driven: a step's branches are whichever attributes its type declares as
                // `is: List, of: ScriptStep`, so a branching step this code has never heard of (a SwitchStep with
                // N branches) is walked with no edit here — this used to be a hardcoded [steps, then, else] list.
                for (nestedName in ScriptConventions.stepBranchAttributeNames(graphNotation, step)) {
                    val nestedAttrLocation = AttributeLocation(step, AttributePath.ofName(nestedName))
                    walkBranch(nestedAttrLocation, graphDefinition, branchOfStep)
                }

                // A ForEachStep's `item` binding, walked by name for exactly the reason the root's `parameters`
                // branch is (see the call site above): binding branches hold a ScriptStep SUBTYPE, which
                // stepBranchAttributeNames excludes by design, and ForEachStep's notation declares no `item`
                // metadata at all. Without this the loop item is absent from branchOfStep, so classifyEdge drops
                // every edge out of it and a body step referencing the item gets no dependency line. Steps with
                // no item branch resolve to an empty list and cost nothing.
                walkBranch(
                    AttributeLocation(step, ScriptConventions.itemAttributePath), graphDefinition, branchOfStep)

                // A `group: true` branch (an IfStep's `branches`) holds structural branch groups, not steps:
                // each child owns its own condition plus a nested step branch. Registering the group child
                // itself is what makes its condition reference an edge (`condition -> IfBranch`, replacing the
                // old `condition -> IfStep`), and recursing into its step branches is what classifies the
                // branch's steps — without which ScriptValueReferences' completeness check would declare the
                // whole document unanalysable and the dependency gutter would lose its lanes. Notation-driven
                // like the branch recursion above: a future N-way construct needs no edit here.
                for (groupName in ScriptConventions.stepGroupAttributeNames(graphNotation, step)) {
                    val groupLocation = AttributeLocation(step, AttributePath.ofName(groupName))
                    for (groupChild in ScriptConventions.orderedDirectChildLocations(
                            graphNotation, groupLocation)) {
                        branchOfStep[groupChild] = groupLocation

                        for (nestedName in
                                ScriptConventions.stepBranchAttributeNames(graphNotation, groupChild)) {
                            walkBranch(
                                AttributeLocation(groupChild, AttributePath.ofName(nestedName)),
                                graphDefinition,
                                branchOfStep)
                        }
                    }
                }
            }
        }


        private fun walkValueScalars(
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

                // A @Service parameter has no notation, so there is no scalar to walk.
                is ServiceAttributeDefinition -> {}
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Every object whose *value* something else reads — an expression naming it, or an attribute referencing it.
     *
     * This is a lower bound on "is it consumed?", not the whole answer: it does not know that a branch's terminal
     * step becomes its container's value (structural containment is deliberately not a data dep — see
     * `classifyEdge`), and it is blind to steps `branchOfStep` never classified. A caller deciding whether a value
     * can be elided must union in the terminals and bail out when the analysis is incomplete — see
     * `ScriptValueReferences`, which does both against the authoritative step tree.
     */
    fun valueReferencedSources(): Set<ObjectLocation> {
        return edges.mapTo(mutableSetOf()) { it.source }
    }


    fun crossBranchEdges(): List<ScriptStepDependency> {
        return edges.filter { branchOfStep[it.source] != branchOfStep[it.target] }
    }


    fun inBranchSourceTargetIndexPairs(stepLocations: List<ObjectLocation>): Set<Pair<Int, Int>> {
        if (stepLocations.isEmpty()) {
            return emptySet()
        }
        val indexByLocation: Map<ObjectLocation, Int> = stepLocations
            .withIndex()
            .associate { (i, loc) -> loc to i }

        val result = mutableSetOf<Pair<Int, Int>>()
        for (edge in edges) {
            val sourceIdx = indexByLocation[edge.source]
                ?: continue
            val targetIdx = indexByLocation[edge.target]
                ?: continue
            if (sourceIdx < targetIdx) {
                result.add(sourceIdx to targetIdx)
            }
        }
        return result
    }


    fun crossBranchOutgoingSourceIndices(stepLocations: List<ObjectLocation>): Set<Int> {
        val indexByLocation: Map<ObjectLocation, Int> = stepLocations
            .withIndex()
            .associate { (i, loc) -> loc to i }

        val result = mutableSetOf<Int>()
        for (edge in edges) {
            val sourceIdx = indexByLocation[edge.source]
                ?: continue
            if (edge.target !in indexByLocation) {
                result.add(sourceIdx)
            }
        }
        return result
    }


    fun crossBranchIncomingTargetIndices(stepLocations: List<ObjectLocation>): Set<Int> {
        val indexByLocation: Map<ObjectLocation, Int> = stepLocations
            .withIndex()
            .associate { (i, loc) -> loc to i }

        val result = mutableSetOf<Int>()
        for (edge in edges) {
            val targetIdx = indexByLocation[edge.target]
                ?: continue
            if (edge.source !in indexByLocation) {
                result.add(targetIdx)
            }
        }
        return result
    }
}
