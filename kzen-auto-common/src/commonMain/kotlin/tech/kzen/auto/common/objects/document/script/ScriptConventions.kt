package tech.kzen.auto.common.objects.document.script

import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.persistentListOf


object ScriptConventions {
    val scriptValidatorLocation = ObjectLocation.parse(
        "auto-jvm/script/script-jvm.yaml#ScriptValidator")

    val objectName = ObjectName("Script")
    val stepObjectName = ObjectName("ScriptStep")
    val runStepObjectName = ObjectName("RunStep")
    val resultStepObjectName = ObjectName("ResultStep")

    val stepsAttributeName = AttributeName("steps")
    val stepsAttributePath = AttributePath.ofName(stepsAttributeName)

    // Branches that hold value bindings (named typed values) rather than executed body steps:
    // the Script's `parameters`, and a ForEachStep's per-iteration `item`. Bindings live here so they
    // are addressable/validated like steps but are rendered outside the body and never executed.
    // `parameters` is the flavour-neutral branch shared with Job, so the constant lives in LogicConventions.
    val parametersAttributeName = LogicConventions.parametersAttributeName
    val parametersAttributePath = LogicConventions.parametersAttributePath

    val itemAttributeName = AttributeName("item")
    val itemAttributePath = AttributePath.ofName(itemAttributeName)

    // A ForEachStep's collection: the Kotlin EXPRESSION whose value it iterates over (compiled in the
    // inference form, so its element type is the loop variable's) — distinct from `item`, the branch that
    // holds the per-iteration ForEachItemBinding.
    val itemsAttributeName = AttributeName("items")
    val itemsAttributePath = AttributePath.ofName(itemsAttributeName)

    // The Script's result signature: a `results` map (component name -> TypeMetadata) parsed by
    // ResultSignatureDefiner into the output TupleDefinition. Not a live step object (unlike parameters) —
    // it is plain data on the main Script object declaring what the Script returns; empty/absent => void.
    // `results` is the flavour-neutral branch shared with Job, so the constant lives in LogicConventions.
    val resultsAttributeName = LogicConventions.resultsAttributeName
    val resultsAttributePath = LogicConventions.resultsAttributePath

    val instructionsAttributeName = AttributeName("instructions")
    val instructionsAttributePath = AttributePath.ofName(instructionsAttributeName)

    // The branching condition of a control step, shared by IfBranch and DoWhileStep — both Kotlin expressions
    // compiled with a forced Boolean return. The name is common; the SCOPING is each step's own (DoWhile
    // marks its condition `scope: body`, an IfBranch's is predecessor-scoped).
    val conditionAttributeName = AttributeName("condition")


    // The kzen-base `List` type object (kzen-lib notation/base/kzen-base.yaml), which attribute metadata names
    // to declare list-ness (`is: List`). kzen-lib has no Kotlin-side constant for it.
    private const val listTypeName = "List"

    // Attribute-metadata key marking an expression attribute whose in-scope references are the declaring step's
    // own BODY steps (children of its branch attributes) rather than its predecessors — DoWhileStep.condition
    // declares `scope: body`. Read by the client KotlinExpressionEditor; the server-side counterpart is the step
    // class's own scope computation (DoWhileStep.conditionScopeTypes). Inert for definition, like `rerun`.
    private const val scopeKey = "scope"
    private const val scopeBodyValue = "body"

    // Attribute-metadata key marking a branch whose children are structural GROUPS rather than steps — an
    // IfStep's `branches`, each child an IfBranch owning its own condition and `steps` sub-branch. Like `rerun`
    // and `scope`, a plain metadata marker: inert for definition, read only by the Script analyses (see
    // [stepGroupAttributeNames]). Every group-aware rule keys off the marker, so an N-way construct shared code
    // has never heard of joins the same semantics declaratively.
    private const val groupKey = "group"
    private val groupSegment = AttributeSegment.ofKey(groupKey)


    /**
     * The branch attributes of [objectLocation]'s type: the attributes whose merged metadata (through the `is:`
     * inheritance chain) declares `is: List, of: ScriptStep` — exactly how IfStep's then/else, ForEachStep's and
     * DoWhileStep's steps, and the root Script's steps are declared. Notation-driven, so a branching step type
     * shared code has never heard of (a SwitchStep with N branches, a third-party plugin step) is discovered
     * without editing anything here.
     *
     * `of:` is matched by exact name, not by inheritance, and that is load-bearing: binding branches are `of:` a
     * ScriptStep SUBTYPE (the Script's `parameters` is `of: ParameterBinding`, itself `is: ScriptStep`), so they
     * are correctly excluded — they hold named values, not executed body steps. A qualified reference
     * (`…#ScriptStep`) would likewise not match; no first-party or sample-plugin notation writes one.
     */
    fun stepBranchAttributeNames(
        graphNotation: GraphNotation,
        objectLocation: ObjectLocation
    ): List<AttributeName> {
        if (objectLocation !in graphNotation.coalesce) {
            // NB: stale location (step deleted or renamed) — the inheritance chain walk would throw
            return listOf()
        }

        val metaNotation = graphNotation.mergeAttribute(
            objectLocation, NotationConventions.metaAttributeName) as? MapAttributeNotation
            ?: return listOf()

        return metaNotation.map.mapNotNull { (attributeSegment, attributeMeta) ->
            val attributeMetaMap = attributeMeta as? MapAttributeNotation
                ?: return@mapNotNull null

            val declaresList = attributeMetaMap.map[NotationConventions.isAttributeSegment]
                ?.asString() == listTypeName

            val declaresStepElements = attributeMetaMap.map[NotationConventions.ofAttributeSegment]
                ?.asString() == stepObjectName.value

            when {
                declaresList && declaresStepElements -> AttributeName(attributeSegment.asKey())
                else -> null
            }
        }
    }


    /**
     * The GROUP attributes of [objectLocation]'s type: the attributes whose merged metadata (through the `is:`
     * inheritance chain) declares `group: true` — IfStep's `branches`, whose children are IfBranch objects, not
     * steps. Exact mirror of [stepBranchAttributeNames], including its stale-location guard.
     *
     * A group child is a structural node the analyses must see through, never treat as a step: it gets no
     * execution band and is not a jump target, its own step branches ARE walked (per-branch recursion
     * everywhere), and it does not enter its siblings' scope (an earlier branch did not run when a later one
     * does — see `ScriptTree.predecessors`).
     *
     * [stepBranchAttributeNames] continues to ignore a group branch for free: it matches `of: ScriptStep` by
     * exact name, and a group branch is `of:` its own group type. The group child's OWN `steps` attribute is
     * discovered as an ordinary step branch, which is what drives the recursion.
     */
    fun stepGroupAttributeNames(
        graphNotation: GraphNotation,
        objectLocation: ObjectLocation
    ): List<AttributeName> {
        if (objectLocation !in graphNotation.coalesce) {
            // NB: stale location (step deleted or renamed) — the inheritance chain walk would throw
            return listOf()
        }

        val metaNotation = graphNotation.mergeAttribute(
            objectLocation, NotationConventions.metaAttributeName) as? MapAttributeNotation
            ?: return listOf()

        return metaNotation.map.mapNotNull { (attributeSegment, attributeMeta) ->
            val attributeMetaMap = attributeMeta as? MapAttributeNotation
                ?: return@mapNotNull null

            when (attributeMetaMap.map[groupSegment]?.asBoolean()) {
                true -> AttributeName(attributeSegment.asKey())
                else -> null
            }
        }
    }


    /**
     * Whether [attributeName] on [objectLocation]'s type is marked `scope: body` in its attribute metadata — read
     * through the `is:` inheritance chain, mirroring ScriptNestingAnalysis.isReRunAttribute, so a concrete step
     * instance inherits the marker from its archetype.
     */
    fun isBodyScopedExpression(
        graphNotation: GraphNotation,
        objectLocation: ObjectLocation,
        attributeName: AttributeName
    ): Boolean {
        if (objectLocation !in graphNotation.coalesce) {
            return false
        }

        val scopePath = AttributePath(
            NotationConventions.metaAttributeName,
            AttributeNesting(persistentListOf(
                AttributeSegment.ofKey(attributeName.value),
                AttributeSegment.ofKey(scopeKey))))

        return graphNotation.firstAttribute(objectLocation, scopePath)?.asString() == scopeBodyValue
    }


    // The steps of a branch in document order: the objects nested directly under attributeLocation's
    // object at its attribute (e.g. main.steps, an IfStep's then/else, a ForEachStep's steps). Order is
    // the document position of the step objects (there is no step-list attribute). Mirrors
    // NestedListAttributeDefiner, which feeds the same list to the executor.
    fun orderedDirectChildLocations(
        graphNotation: GraphNotation,
        attributeLocation: AttributeLocation
    ): List<ObjectLocation> {
        val containingLocation = attributeLocation.objectLocation
        val documentNotation = graphNotation.documents[containingLocation.documentPath]
            ?: return listOf()
        return documentNotation
            .directNestedObjectPaths(
                containingLocation.objectPath, attributeLocation.attributePath.attribute)
            .map { ObjectLocation(containingLocation.documentPath, it) }
    }


    /** Whether [stepLocation] is a RunStep — by inheritance chain, so a subtype or a plugin's RunStep matches. */
    fun isRunStep(graphNotation: GraphNotation, stepLocation: ObjectLocation): Boolean {
        if (stepLocation !in graphNotation.coalesce) {
            return false
        }
        return graphNotation
            .inheritanceChain(stepLocation)
            .any { it.objectPath.name == runStepObjectName }
    }


    /** Whether [stepLocation] is a Result step — by inheritance chain, so a subtype matches. */
    fun isResultStep(graphNotation: GraphNotation, stepLocation: ObjectLocation): Boolean {
        if (stepLocation !in graphNotation.coalesce) {
            return false
        }
        return graphNotation
            .inheritanceChain(stepLocation)
            .any { it.objectPath.name == resultStepObjectName }
    }


    /**
     * The document a RunStep hosts, resolved from its `instructions` reference; null when it names nothing or
     * nothing resolvable. Callers that reason about the caller/callee pair (the context analysis, the step
     * badges) all need this one resolution.
     */
    fun hostedDocumentPath(graphNotation: GraphNotation, stepLocation: ObjectLocation): DocumentPath? {
        val instructions = (graphNotation.firstAttribute(stepLocation, instructionsAttributePath)
                as? ScalarAttributeNotation)
            ?.value
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return graphNotation.coalesce
            .locateOptional(ObjectReference.parse(instructions), ObjectReferenceHost.ofLocation(stepLocation))
            ?.documentPath
    }


    fun isScript(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == objectName.value
    }
}