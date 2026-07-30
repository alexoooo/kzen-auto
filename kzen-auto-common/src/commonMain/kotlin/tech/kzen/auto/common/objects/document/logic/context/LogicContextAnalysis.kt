package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Which steps of a Script document ask for a run-scoped Context nothing upstream provides — and the
 * neighbouring declaration mistakes the same walk can see. Pure notation: it reads declarations only, so it
 * needs no type inference, no definition and no instantiation, and it answers for the document open in the
 * editor with no knowledge of who calls it.
 *
 * **An unsatisfied requirement is an ERROR, not advice.** Availability is decidable from declarations alone,
 * because the only way a caller can supply a Context is for this document to name it in its own
 * `context.requires` — so a requirement nothing satisfies could never have succeeded, and Run is disabled.
 * The converse costs nothing and gets no finding: a resource provided and never consumed is a legitimate
 * pattern. The remaining findings are advisory warnings — a dangling reference, a shared resource key, an
 * export nothing in the document can back, a retired `context.slots` declaration.
 *
 * **Conditionals resolve in the direction that suppresses findings.** A `provides` inside an If/loop branch
 * counts as available for everything after it, and a `releases` inside one does NOT remove availability. A
 * false error would block a Run that works, so a missed one is the cheaper mistake.
 *
 * **The cross-document rule is where this earns its keep.** At a RunStep it reads the hosted document's
 * export signature — a declaration, so no recursion — and reasons about it against the caller's own. See
 * [analyzeRunStep].
 *
 * Granularity note: like the runtime gate, this reasons at Context granularity and never at qualifier
 * granularity — a qualifier is a step parameter and may be computed, so "is SOME SUT started" is the most any
 * declaration-driven layer can answer.
 */
object LogicContextAnalysis {
    //-----------------------------------------------------------------------------------------------------------------
    private val closePolicyAttributeName = AttributeName("closePolicy")
    private val closePolicyAttributePath = AttributePath.ofName(closePolicyAttributeName)


    fun analyze(graphNotation: GraphNotation, documentPath: DocumentPath): LogicContextFindings {
        if (documentPath !in graphNotation.documents) {
            return LogicContextFindings.empty
        }

        val errors = mutableMapOf<ObjectPath, MutableList<String>>()
        val warnings = mutableMapOf<ObjectPath, MutableList<String>>()

        fun error(objectPath: ObjectPath, message: String) {
            errors.getOrPut(objectPath) { mutableListOf() }.add(message)
        }

        fun warn(objectPath: ObjectPath, message: String) {
            warnings.getOrPut(objectPath) { mutableListOf() }.add(message)
        }

        danglingAndAliasWarnings(graphNotation, documentPath, ::warn)
        signatureWarnings(graphNotation, documentPath, ::warn)

        // Seeded from the document's own `context.requires`: the author asserting that a caller provides
        // these. That assertion is the legitimate escape hatch for a sub-script — and it is why a requiring
        // sub-script's own steps never error in its own document view; the breakage surfaces at the CALLER's
        // RunStep instead.
        val available = LogicContextConventions
            .documentRequires(graphNotation, documentPath)
            .map { it.location }
            .toMutableSet()

        val unexportedProvides = mutableMapOf<ObjectLocation, DocumentPath>()

        val tree = ScriptTree.read(documentPath, graphNotation)
        walk(graphNotation, documentPath, tree, available, unexportedProvides, nested = false, error = ::error)

        return LogicContextFindings(
            errors.mapValues { it.value.joinToString(" ") },
            warnings.mapValues { it.value.joinToString(" ") })
    }


    //----------------------------------------------------------------------------------------- signature helpers
    /**
     * The Contexts [documentPath] is in a position to hand upward: one its own steps open, or one a document it
     * hosts exports to it. The basis of the declared-but-unbackable export warning, and rendered directly by
     * the signature editor.
     *
     * **One level deep**, and cycle-free for exactly that reason: a callee's export contract is a declaration,
     * never a walk into the callee's own callees. The known false negative — two documents each declaring an
     * export backed only by a RunStep to the other — passes this check. It is warning-severity and
     * pathological authoring, so it is accepted rather than paid for with a whole-graph traversal.
     */
    fun canProvide(graphNotation: GraphNotation, documentPath: DocumentPath): Set<ObjectLocation> {
        val documentNotation = graphNotation.documents[documentPath]
            ?: return setOf()

        val result = mutableSetOf<ObjectLocation>()

        for ((descriptor, _) in ownStepProvides(graphNotation, documentPath)) {
            result.add(descriptor.location)
        }

        for (objectPath in documentNotation.objects.notations.map.keys) {
            val objectLocation = ObjectLocation(documentPath, objectPath)
            if (! ScriptConventions.isRunStep(graphNotation, objectLocation)) {
                continue
            }

            val hostedPath = ScriptConventions.hostedDocumentPath(graphNotation, objectLocation)
                ?: continue

            LogicContextConventions
                .documentExports(graphNotation, hostedPath)
                .forEach { result.add(it.location) }
        }

        return result
    }


    /** The `context.exports` entries [canProvide] cannot back — the declaration is an unkeepable promise. */
    fun unbackedExports(graphNotation: GraphNotation, documentPath: DocumentPath): List<ContextDescriptor> {
        val canProvide = canProvide(graphNotation, documentPath)
        return LogicContextConventions
            .documentExports(graphNotation, documentPath)
            .filter { it.location !in canProvide }
    }


    /** Present only in notation written against the retired capture model; the key has no effect. */
    fun legacySlotReferences(graphNotation: GraphNotation, documentPath: DocumentPath): List<String> {
        return LogicContextConventions.legacyDocumentSlotReferences(graphNotation, documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun walk(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        node: ScriptTree,
        available: MutableSet<ObjectLocation>,
        unexportedProvides: MutableMap<ObjectLocation, DocumentPath>,
        nested: Boolean,
        error: (ObjectPath, String) -> Unit
    ) {
        val nodeLocation = ObjectLocation(documentPath, node.objectPath)
        val stepBranches = ScriptConventions
            .stepBranchAttributeNames(graphNotation, nodeLocation)
            .toHashSet()
        val groupBranches = ScriptConventions
            .stepGroupAttributeNames(graphNotation, nodeLocation)
            .toHashSet()

        for ((attributeName, childTrees) in node.children) {
            when (attributeName) {
                // A group child (an IfBranch) is structural, never executed: descend through it, but its
                // contents are conditional, so they enter the finding-suppressing nested mode.
                in groupBranches ->
                    for (childTree in childTrees) {
                        walk(graphNotation, documentPath, childTree, available, unexportedProvides,
                            nested = true, error = error)
                    }

                in stepBranches ->
                    for (childTree in childTrees) {
                        visitStep(graphNotation, documentPath, childTree, available, unexportedProvides,
                            nested, error)
                    }

                // A binding branch (`parameters`, a loop's `item`) holds named values, not executed steps.
                else ->
                    continue
            }
        }
    }


    private fun visitStep(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        node: ScriptTree,
        available: MutableSet<ObjectLocation>,
        unexportedProvides: MutableMap<ObjectLocation, DocumentPath>,
        nested: Boolean,
        error: (ObjectPath, String) -> Unit
    ) {
        val stepLocation = ObjectLocation(documentPath, node.objectPath)

        for (required in LogicContextConventions.stepRequires(graphNotation, stepLocation)) {
            if (required.location !in available) {
                error(node.objectPath,
                    "Requires ${required.label()}, which nothing before it provides. " +
                            remedyFor(required, unexportedProvides))
            }
        }

        if (ScriptConventions.isRunStep(graphNotation, stepLocation)) {
            analyzeRunStep(
                graphNotation, documentPath, node.objectPath, stepLocation, available, unexportedProvides, error)
        }

        // A step's own provide is available for the rest of this document whether or not the document exports
        // it: an exported registration rests on an ancestor frame, and resource reads walk self → root.
        LogicContextConventions.stepProvides(graphNotation, stepLocation)?.let {
            available.add(it.location)
        }

        // Removing availability is the one thing a conditional must NOT do — a Close inside an If branch may
        // not have run. At the top level it does remove, which is what catches a browser step placed after a
        // Close step.
        LogicContextConventions.stepReleases(graphNotation, stepLocation)?.let {
            if (! nested) {
                available.remove(it.location)
            }
        }

        // Nested branches run after the step's own declarations are applied — a loop's body sees what the
        // loop step itself provided.
        walk(graphNotation, documentPath, node, available, unexportedProvides, nested = true, error = error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The cross-document rule, in both directions.
     *
     * **Hosted requires.** Each Context the hosted document H declares in its own `context.requires` must be
     * available at this call site, or the call cannot work — checked against availability as it stands BEFORE
     * the call, since only what precedes a RunStep can satisfy it. This is where a deleted provide actually
     * surfaces: H's own steps stay clean, because H's `context.requires` seeds H's local analysis.
     *
     * **What the call makes available.** H's `context.exports` — its declared contract, so no recursion is
     * needed: if H exports X, H asserts it delivers X, whether from a step of its own or a re-export of its
     * own callee. Plus any Context some step of H provides with `closePolicy: manual`, which reaches this
     * frame through the engine's hand-up at H's settle rather than through the export chain. That manual rule
     * is deliberately ONE level deep — a Manual resource opened two levels down with nothing exported is not
     * modelled, and the remedy is `context.exports`, which the error names.
     *
     * A Context H provides but neither exports nor opens as Manual is **private to H** and correctly absent
     * from availability. That is not a finding in itself — privacy is the default and usually the intent — but
     * it is recorded, so a later error for the same Context can name H and the one-line fix.
     */
    private fun analyzeRunStep(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        stepObjectPath: ObjectPath,
        stepLocation: ObjectLocation,
        available: MutableSet<ObjectLocation>,
        unexportedProvides: MutableMap<ObjectLocation, DocumentPath>,
        error: (ObjectPath, String) -> Unit
    ) {
        val hostedPath = ScriptConventions.hostedDocumentPath(graphNotation, stepLocation)
            ?: return

        if (hostedPath == documentPath) {
            // Direct self-recursion: nothing to say about it here, and walking it would not terminate.
            return
        }

        for (hostedRequired in LogicContextConventions.documentRequires(graphNotation, hostedPath)) {
            if (hostedRequired.location !in available) {
                error(stepObjectPath,
                    "${hostedPath.asString()} requires ${hostedRequired.label()}, " +
                            "which nothing before this step provides. " +
                            remedyFor(hostedRequired, unexportedProvides))
            }
        }

        val hostedExports = LogicContextConventions
            .documentExports(graphNotation, hostedPath)
            .map { it.location }
            .toSet()

        available.addAll(hostedExports)

        for ((descriptor, anyManual) in ownStepProvides(graphNotation, hostedPath)) {
            if (descriptor.location in hostedExports) {
                continue
            }

            if (anyManual) {
                available.add(descriptor.location)
            }
            else {
                unexportedProvides[descriptor.location] = hostedPath
            }
        }
    }


    /**
     * How to make an unsatisfied [descriptor] satisfiable. When an earlier callee provides it privately the fix
     * is exact and worth naming — that document's `context.exports` — which is the whole diagnostic value a
     * standing "this provide escapes into nothing" warning would carry, delivered at the point of failure
     * instead of as noise on every legitimate private provide. Otherwise all three real remedies are listed,
     * because the analysis genuinely cannot tell which one the author wants: nothing it can see provides the
     * Context, including anything a callee opens more than one level down.
     */
    private fun remedyFor(
        descriptor: ContextDescriptor,
        unexportedProvides: Map<ObjectLocation, DocumentPath>
    ): String {
        val providing = unexportedProvides[descriptor.location]
            ?: return "Add a step that provides it, add it to the context exports of a document this one " +
                    "runs, or declare it in this document's context requires so a caller supplies it."

        return "${providing.asString()} provides it but does not export it — add ${descriptor.label()} to " +
                "that document's context exports."
    }


    /**
     * Every Context a step of the document at [documentPath] provides, paired with whether ANY of its
     * providing steps declares `closePolicy: manual` — the flag that decides whether the provide reaches the
     * caller without being exported. Collected over the whole document rather than in execution order: the
     * question is only whether the document provides it at all.
     */
    private fun ownStepProvides(
        graphNotation: GraphNotation,
        documentPath: DocumentPath
    ): List<Pair<ContextDescriptor, Boolean>> {
        val documentNotation = graphNotation.documents[documentPath]
            ?: return listOf()

        val result = LinkedHashMap<ObjectLocation, Pair<ContextDescriptor, Boolean>>()

        for (objectPath in documentNotation.objects.notations.map.keys) {
            val objectLocation = ObjectLocation(documentPath, objectPath)
            val provides = LogicContextConventions.stepProvides(graphNotation, objectLocation)
                ?: continue

            val manual = closePolicyOf(graphNotation, objectLocation) == ResourceClosePolicy.Manual
            val existing = result[provides.location]
            result[provides.location] = provides to ((existing?.second ?: false) || manual)
        }

        return result.values.toList()
    }


    private fun closePolicyOf(graphNotation: GraphNotation, stepLocation: ObjectLocation): ResourceClosePolicy? {
        val value = (graphNotation.firstAttribute(stepLocation, closePolicyAttributePath)
                as? ScalarAttributeNotation)
            ?.value
            ?: return null
        return runCatching { ResourceClosePolicy.parse(value) }.getOrNull()
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The document-level signature mistakes: an export nothing can back, and a retired `slots` declaration. */
    private fun signatureWarnings(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        warn: (ObjectPath, String) -> Unit
    ) {
        for (descriptor in unbackedExports(graphNotation, documentPath)) {
            warn(NotationConventions.mainObjectPath,
                "Exports ${descriptor.label()}, which nothing in this document can provide — " +
                        "no step provides it and no document it runs exports it.")
        }

        if (legacySlotReferences(graphNotation, documentPath).isNotEmpty()) {
            warn(NotationConventions.mainObjectPath,
                "Context slots has no effect: declare context exports on the document that provides the " +
                        "resource, which is the document that decides whether to hand it up.")
        }
    }


    /**
     * The two authoring mistakes that are not about availability: a declaration naming something that is not
     * a Context (or nothing at all), and two Contexts declaring the same `key`.
     *
     * A dangling reference is silent by design elsewhere — these declarations are weak (`by: Nominal`), so a
     * bad name does not fail the object's definition (renames DO rewrite them, via the refactor's weak-edge
     * walk, but a hand-typed miss can still dangle). This warning is the ONLY thing that surfaces it.
     *
     * Duplicate keys are checked GRAPH-WIDE rather than per document, because the case worth catching is a
     * plugin's Context aliasing a first-party one — two objects declaring `key: browser` silently share one
     * registration. That aliasing is deliberate (it is what makes the typed and raw APIs interoperate), so it
     * is reported, never prevented.
     */
    private fun danglingAndAliasWarnings(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        warn: (ObjectPath, String) -> Unit
    ) {
        val aliasedKeys = ContextConventions
            .allContexts(graphNotation)
            .groupBy { it.key }
            .filterValues { it.size > 1 }

        fun check(objectPath: ObjectPath, attributePath: AttributePath, label: String) {
            val objectLocation = ObjectLocation(documentPath, objectPath)
            val references = LogicContextConventions
                .stepContextReferences(graphNotation, objectLocation, attributePath)

            for (reference in references) {
                val descriptor = ContextConventions.resolveOrNull(graphNotation, reference, objectLocation)
                if (descriptor == null) {
                    warn(objectPath, "$label names '$reference', which is not a context.")
                    continue
                }

                aliasedKeys[descriptor.key]?.let { aliases ->
                    warn(objectPath,
                        "${descriptor.label()} shares the resource key '${descriptor.key}' with " +
                                aliases.filter { it.location != descriptor.location }
                                    .joinToString { it.label() } +
                                " — they are the same registration at run time.")
                }
            }
        }

        val documentNotation = graphNotation.documents[documentPath]
            ?: return

        for (objectPath in documentNotation.objects.notations.map.keys) {
            if (objectPath == NotationConventions.mainObjectPath) {
                check(objectPath, LogicContextConventions.documentExportsAttributePath, "Context exports")
                check(objectPath, LogicContextConventions.documentRequiresAttributePath, "Context requires")
                continue
            }

            check(objectPath, LogicContextConventions.providesAttributePath, "Provides")
            check(objectPath, LogicContextConventions.requiresAttributePath, "Requires")
            check(objectPath, LogicContextConventions.releasesAttributePath, "Releases")
        }
    }
}
