package tech.kzen.auto.common.paradigm.logic

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation


/**
 * The document-level call graph induced by hosting objects: which Logic documents call which.
 *
 * A hosting object links its callee through an attribute that is `by: Nominal` — a WEAK reference, by design
 * (the callee graph must not join the caller's instantiation) — so the callee never enters the caller's
 * transitive definition closure, and nothing in the compiled graph exposes the edge. It is therefore
 * discovered purely from notation: any object with an attribute whose METADATA declares `is: ObjectLocation`,
 * whose value resolves to a location in ANOTHER document whose `main` is a runnable Logic
 * ([AutoConventions.isLogic]). That matches Script `RunStep.instructions`, Flow `RunLogic.instructions`, and
 * Job `RunWorker.instructions` without naming any flavour — a third-party hosting step gets the behaviour for
 * free (no step-type or flavour branch here, per the extensibility rule).
 *
 * Naturally excluded, with no special cases: intra-document `is: ObjectLocation` references (step references,
 * `selfLocation`) target the same document; `is: Map` / `is: List` attributes are not scalar `ObjectLocation`;
 * blank values (archetype defaults) and dangling / unparseable links are skipped. Best-effort throughout — a
 * mid-edit broken document contributes no edges rather than throwing, so both consumers stay well-defined
 * while the user is typing.
 *
 * Both traversals EXCLUDE the document they start from, unless a call cycle genuinely reaches back to it
 * (self-hosting and mutual hosting are legal at runtime). Consumers that want the seed included add it.
 */
object LogicCallGraph {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Every Logic document reachable from [documentPath] by following its hosting objects, recursively — the
     * documents whose notation a run rooted here can actually reach.
     */
    fun transitiveCallees(
        graphStructure: GraphStructure,
        documentPath: DocumentPath
    ): Set<DocumentPath> {
        // NB: only the reachable documents are scanned for edges (the reverse direction below has to scan all
        // of them), so a big project with many unrelated documents costs no more than its own closure
        val objectsByDocument = graphStructure.graphNotation.coalesce.map.entries
            .groupBy { it.key.documentPath }

        val callees = mutableSetOf<DocumentPath>()
        val open = ArrayDeque<DocumentPath>()
        open.add(documentPath)

        while (open.isNotEmpty()) {
            val caller = open.removeFirst()

            for ((objectLocation, objectNotation) in objectsByDocument[caller] ?: continue) {
                forEachCalleeDocument(graphStructure, objectLocation, objectNotation) { callee ->
                    if (callees.add(callee)) {
                        open.add(callee)
                    }
                }
            }
        }

        return callees
    }


    /**
     * Every Logic document that transitively calls [documentPath] — the inverse of [transitiveCallees]. Adding
     * a call FROM one of these TO [documentPath] would close a cycle, which is why the callee-picking editor
     * drops them from its suggestions.
     */
    fun transitiveCallers(
        graphStructure: GraphStructure,
        documentPath: DocumentPath
    ): Set<DocumentPath> {
        // callee document -> the documents that call it
        val callersByCallee = mutableMapOf<DocumentPath, MutableSet<DocumentPath>>()

        for ((objectLocation, objectNotation) in graphStructure.graphNotation.coalesce.map) {
            forEachCalleeDocument(graphStructure, objectLocation, objectNotation) { callee ->
                callersByCallee.getOrPut(callee) { mutableSetOf() }.add(objectLocation.documentPath)
            }
        }

        val callers = mutableSetOf<DocumentPath>()
        val open = ArrayDeque<DocumentPath>()
        open.add(documentPath)

        while (open.isNotEmpty()) {
            val callee = open.removeFirst()

            for (caller in callersByCallee[callee] ?: continue) {
                if (callers.add(caller)) {
                    open.add(caller)
                }
            }
        }

        return callers
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The edge definition itself - the single place that decides what counts as a logic call, shared by both
    // directions above.
    private inline fun forEachCalleeDocument(
        graphStructure: GraphStructure,
        objectLocation: ObjectLocation,
        objectNotation: ObjectNotation,
        visit: (DocumentPath) -> Unit
    ) {
        val objectMetadata = graphStructure.graphMetadata.objectMetadata.map[objectLocation]
            ?: return

        for ((attributeName, attributeMetadata) in objectMetadata.attributes.map) {
            if (attributeMetadata.type?.className != ObjectLocation.className) {
                continue
            }

            val target = resolveLogicLink(
                graphStructure.graphNotation, objectLocation, objectNotation.get(attributeName)?.asString())
                ?: continue

            if (target.documentPath == objectLocation.documentPath) {
                continue
            }

            visit(target.documentPath)
        }
    }


    // Resolved against the hosting object, the way kzen-lib's own definer resolves the same attribute, so a
    // document-relative link is seen here exactly as it will be seen at definition time.
    private fun resolveLogicLink(
        graphNotation: GraphNotation,
        host: ObjectLocation,
        attributeValue: String?
    ): ObjectLocation? {
        if (attributeValue.isNullOrBlank()) {
            return null
        }

        return try {
            val reference = ObjectReference.parse(attributeValue)
            graphNotation.coalesce
                .locateOptional(reference, ObjectReferenceHost.ofLocation(host))
                ?.takeIf { AutoConventions.isLogic(graphNotation, it.documentPath) }
        }
        catch (_: Exception) {
            null
        }
    }
}
