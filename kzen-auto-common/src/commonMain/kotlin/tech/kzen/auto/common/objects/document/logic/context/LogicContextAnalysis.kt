package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Which steps of a Script document ask for a run-scoped Context nothing upstream provides — and the three
 * neighbouring authoring mistakes the same walk can see. Pure notation: it reads declarations only, so it
 * needs no type inference, no definition and no instantiation, and it answers for the document open in the
 * editor with no knowledge of who calls it.
 *
 * Everything it produces is a WARNING, never an error. That is a deliberate stance, not timidity: a document
 * whose requirement nothing local satisfies may be perfectly correct when its caller provides one, and the
 * editor cannot see the caller. Run is never blocked by anything here; the strict half lives at execution,
 * where the Script spine fails a step whose declared requirement is genuinely absent.
 *
 * **Conditionals resolve in the direction that suppresses warnings.** A `provides` inside an If/loop branch
 * counts as available for everything after it, and a `releases` inside one does NOT remove availability. The
 * analysis is advisory, so a false warning costs more than a missed one.
 *
 * **The cross-document rule is where this earns its keep.** At a RunStep it reasons about the hosted
 * document's declarations against the caller's, catching the migration mistake — a provide that escapes into
 * nothing — rather than only the authoring mistake. See [analyzeRunStep].
 *
 * Granularity note: like the runtime gate, this reasons at Context granularity and never at qualifier
 * granularity — a qualifier is a step parameter and may be computed, so "is SOME SUT started" is the most any
 * declaration-driven layer can answer.
 */
object LogicContextAnalysis {
    //-----------------------------------------------------------------------------------------------------------------
    private val closePolicyAttributeName = AttributeName("closePolicy")
    private val closePolicyAttributePath = AttributePath.ofName(closePolicyAttributeName)


    /**
     * Warnings for [documentPath], keyed by the object path they attach to — a step, or `main` for a
     * document-level declaration problem. At most one entry per object; several findings on one object are
     * joined into one message.
     */
    fun analyze(graphNotation: GraphNotation, documentPath: DocumentPath): Map<ObjectPath, String> {
        if (documentPath !in graphNotation.documents) {
            return mapOf()
        }

        val warnings = mutableMapOf<ObjectPath, MutableList<String>>()

        fun warn(objectPath: ObjectPath, message: String) {
            warnings.getOrPut(objectPath) { mutableListOf() }.add(message)
        }

        danglingAndAliasWarnings(graphNotation, documentPath, ::warn)

        // Seeded from the document's own `context.requires`: the author asserting that a caller provides
        // these. That assertion is the legitimate escape hatch for a sub-script — and it is why a requiring
        // sub-script's own steps never amber in its own document view; the breakage surfaces at the CALLER's
        // RunStep instead.
        val available = LogicContextConventions
            .documentRequires(graphNotation, documentPath)
            .map { it.location }
            .toMutableSet()

        val tree = ScriptTree.read(documentPath, graphNotation)
        walk(graphNotation, documentPath, tree, available, nested = false, warn = ::warn)

        return warnings.mapValues { it.value.joinToString(" ") }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun walk(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        node: ScriptTree,
        available: MutableSet<ObjectLocation>,
        nested: Boolean,
        warn: (ObjectPath, String) -> Unit
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
                // contents are conditional, so they enter the warning-suppressing nested mode.
                in groupBranches ->
                    for (childTree in childTrees) {
                        walk(graphNotation, documentPath, childTree, available, nested = true, warn = warn)
                    }

                in stepBranches ->
                    for (childTree in childTrees) {
                        visitStep(graphNotation, documentPath, childTree, available, nested, warn)
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
        nested: Boolean,
        warn: (ObjectPath, String) -> Unit
    ) {
        val stepLocation = ObjectLocation(documentPath, node.objectPath)

        for (required in LogicContextConventions.stepRequires(graphNotation, stepLocation)) {
            if (required.location !in available) {
                warn(node.objectPath,
                    "Requires ${required.label()}, which nothing before it provides. " +
                            "Add a step that provides it, or declare it in this document's context requires " +
                            "so a caller supplies it.")
            }
        }

        if (isRunStep(graphNotation, stepLocation)) {
            analyzeRunStep(graphNotation, documentPath, node.objectPath, stepLocation, available, warn)
        }

        // A step that provides X directly is unambiguous: with no slot anywhere it binds to this document
        // (the engine's Self fallback), so it is available for the rest of this document either way.
        LogicContextConventions.stepProvides(graphNotation, stepLocation)?.let {
            available.add(it.location)
        }

        // Removing availability is the one thing a conditional must NOT do — a Close inside an If branch may
        // not have run. At the top level it does remove, which is what catches a browser step placed after a
        // Close step; nothing caught that before.
        LogicContextConventions.stepReleases(graphNotation, stepLocation)?.let {
            if (! nested) {
                available.remove(it.location)
            }
        }

        // Nested branches run after the step's own declarations are applied — a loop's body sees what the
        // loop step itself provided.
        walk(graphNotation, documentPath, node, available, nested = true, warn = warn)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The cross-document rule, in both directions.
     *
     * **Escaping provides.** A Context the hosted document H provides, and H's own slots do not own, does not
     * automatically reach the caller: §6's ownership rule binds an unslotted provide to H's own node, where
     * it dies at H's settle. So the caller must be able to name a reason the resource survives, and there are
     * exactly three:
     *
     * 1. the providing step's `closePolicy` is `manual` — the engine's hand-up walks the registration one
     *    level up at H's settle, so it escapes with no slot at all;
     * 2. this document declares a slot for it — it binds here;
     * 3. this document's own `context.requires` names it — the author has asserted an outer owner that the
     *    local analysis cannot see.
     *
     * With none of the three, the provide is disposed the moment H finishes, and everything downstream that
     * depends on it is broken at run time while the notation looks clean. That is exactly the mistake a
     * migration from the old reach-up policies produces, which is why this is the single most valuable
     * diagnostic here.
     *
     * **Hosted requires.** The same pass checks the other direction: each Context H declares in its own
     * `context.requires` must be available at this call site, or the call cannot work. This is where a
     * deleted slot actually surfaces — the requiring sub-script's own steps stay clean, because its
     * `context.requires` seeds its local analysis.
     */
    private fun analyzeRunStep(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        stepObjectPath: ObjectPath,
        stepLocation: ObjectLocation,
        available: MutableSet<ObjectLocation>,
        warn: (ObjectPath, String) -> Unit
    ) {
        val hostedPath = hostedDocumentPath(graphNotation, stepLocation)
            ?: return

        if (hostedPath == documentPath) {
            // Direct self-recursion: nothing to say about it here, and walking it would not terminate.
            return
        }

        val hostedSlots = LogicContextConventions
            .documentSlots(graphNotation, hostedPath)
            .map { it.location }
            .toSet()

        val callerSlots = LogicContextConventions
            .documentSlots(graphNotation, documentPath)
            .map { it.location }
            .toSet()

        for ((descriptor, anyManual) in hostedProvides(graphNotation, hostedPath)) {
            if (descriptor.location in hostedSlots) {
                // H owns it itself — it never escapes, and was never meant to.
                continue
            }

            val escapes =
                anyManual ||
                descriptor.location in callerSlots ||
                descriptor.location in available

            if (escapes) {
                available.add(descriptor.location)
            }
            else {
                warn(stepObjectPath,
                    "${hostedPath.asString()} provides ${descriptor.label()} but no enclosing slot owns it — " +
                            "it is disposed when that document finishes. Declare a ${descriptor.label()} slot " +
                            "on this document, or on a caller plus a context requires here.")
            }
        }

        for (hostedRequired in LogicContextConventions.documentRequires(graphNotation, hostedPath)) {
            if (hostedRequired.location !in available) {
                warn(stepObjectPath,
                    "${hostedPath.asString()} requires ${hostedRequired.label()}, " +
                            "which nothing before this step provides.")
            }
        }
    }


    /**
     * Every Context the document at [hostedPath] provides, paired with whether ANY of its providing steps
     * declares `closePolicy: manual` — the flag that decides whether the provide escapes without a slot.
     * Collected over the whole document rather than in execution order: the question is only whether the
     * document provides it at all.
     */
    private fun hostedProvides(
        graphNotation: GraphNotation,
        hostedPath: DocumentPath
    ): List<Pair<ContextDescriptor, Boolean>> {
        val documentNotation = graphNotation.documents[hostedPath]
            ?: return listOf()

        val result = LinkedHashMap<ObjectLocation, Pair<ContextDescriptor, Boolean>>()

        for (objectPath in documentNotation.objects.notations.map.keys) {
            val objectLocation = ObjectLocation(hostedPath, objectPath)
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


    private fun isRunStep(graphNotation: GraphNotation, stepLocation: ObjectLocation): Boolean {
        if (stepLocation !in graphNotation.coalesce) {
            return false
        }
        return graphNotation
            .inheritanceChain(stepLocation)
            .any { it.objectPath.name == ScriptConventions.runStepObjectName }
    }


    private fun hostedDocumentPath(graphNotation: GraphNotation, stepLocation: ObjectLocation): DocumentPath? {
        val instructions = (graphNotation.firstAttribute(
                stepLocation, ScriptConventions.instructionsAttributePath) as? ScalarAttributeNotation)
            ?.value
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return graphNotation.coalesce
            .locateOptional(ObjectReference.parse(instructions), ObjectReferenceHost.ofLocation(stepLocation))
            ?.documentPath
    }


    //-----------------------------------------------------------------------------------------------------------------
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
                check(objectPath, LogicContextConventions.slotsAttributePath, "Context slot")
                check(objectPath, LogicContextConventions.documentRequiresAttributePath, "Context requires")
                continue
            }

            check(objectPath, LogicContextConventions.providesAttributePath, "Provides")
            check(objectPath, LogicContextConventions.requiresAttributePath, "Requires")
            check(objectPath, LogicContextConventions.releasesAttributePath, "Releases")
        }
    }
}
