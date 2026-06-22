package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.auto.common.util.KotlinExpressionAnalyzer
import tech.kzen.lib.common.model.attribute.AttributeName
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


        // NB: the IfStep "then" / "else" branch names are owned by IfStep, but the analyzer needs
        //     to recurse through every branch type the script supports, including nested IfSteps.
        //     The list is duplicated from each step's controller name; revisit if a new branching
        //     step is introduced (e.g. SwitchStep with N branches).
        private val branchAttributeNames = listOf(
            ScriptConventions.stepsAttributeName,
            AttributeName("then"),
            AttributeName("else"))


        fun analyze(
            graphDefinitionAttempt: GraphDefinitionAttempt,
            documentPath: DocumentPath
        ): ScriptDependencyAnalysis {
            val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation
            val coalesce = graphNotation.coalesce
            val mainObjectLocation = documentPath.toMainObjectLocation()

            val branchOfStep = mutableMapOf<ObjectLocation, AttributeLocation>()
            walkBranch(
                AttributeLocation(mainObjectLocation, ScriptConventions.stepsAttributePath),
                graphDefinitionAttempt,
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
                graphDefinitionAttempt,
                branchOfStep)

            // Key every in-document object by the bare identifier content its name maps to, so a step named
            // "my step" (referenced as `` `my step` ``) is matched — the old plain-identifier regex missed it.
            // Collisions (two names escaping to the same identifier) keep the last, a documented limitation.
            val locationByIdentifierContent = coalesce.map.keys
                .asSequence()
                .filter { it.documentPath == documentPath }
                .associate { location ->
                    ExpressionUtils.identifierContent(
                        ExpressionUtils.escapeKotlinVariableName(location.objectPath.name.value)
                    ) to location
                }

            val edges = mutableSetOf<ScriptStepDependency>()

            fun classifyEdge(sourceLocation: ObjectLocation, targetLocation: ObjectLocation) {
                if (sourceLocation == targetLocation || sourceLocation.documentPath != documentPath) {
                    return
                }
                // NB: structural containment (e.g. IfStep.then[*] → its child steps) is not a data dep.
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
                val objectDefinition = graphDefinitionAttempt.objectDefinitions[targetLocation]
                    ?: continue
                val host = ObjectReferenceHost.ofLocation(targetLocation)

                for ((_, definitionReference) in objectDefinition.attributeReferencesIncludingWeak()) {
                    val resolved = coalesce.locateOptional(definitionReference.objectReference, host)
                        ?: continue
                    classifyEdge(resolved, targetLocation)
                }

                // NB: scan only value-typed scalar strings (catches code-attribute refs like FormulaStep.code).
                //     Reference-typed subtrees are skipped to avoid matching identifier paths like
                //     "main.steps/If.then/Formula 3" as the word "Formula".
                val objectNotation = coalesce[targetLocation]
                    ?: continue
                walkValueScalars(objectDefinition, objectNotation) { stringValue ->
                    // Lexer-derived references: respects strings/comments/back-ticks and skips member selectors,
                    // unlike the previous word-boundary regex (see KotlinExpressionAnalyzer).
                    for (referencedIdentifier in KotlinExpressionAnalyzer.referencedIdentifiers(stringValue)) {
                        val sourceLocation = locationByIdentifierContent[referencedIdentifier]
                            ?: continue
                        if (sourceLocation == targetLocation) {
                            continue
                        }
                        classifyEdge(sourceLocation, targetLocation)
                    }
                }
            }

            return ScriptDependencyAnalysis(branchOfStep, edges)
        }


        private fun walkBranch(
            branchAttributeLocation: AttributeLocation,
            graphDefinitionAttempt: GraphDefinitionAttempt,
            branchOfStep: MutableMap<ObjectLocation, AttributeLocation>
        ) {
            // Steps are the objects nested under this branch attribute, in document order — probing a branch
            // a step doesn't have (e.g. Run.steps) just yields an empty list.
            val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation
            val steps = ScriptConventions.orderedDirectChildLocations(
                graphNotation, branchAttributeLocation)

            for (step in steps) {
                branchOfStep[step] = branchAttributeLocation
            }
            for (step in steps) {
                for (nestedName in branchAttributeNames) {
                    val nestedAttrLocation = AttributeLocation(step, AttributePath.ofName(nestedName))
                    walkBranch(nestedAttrLocation, graphDefinitionAttempt, branchOfStep)
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
