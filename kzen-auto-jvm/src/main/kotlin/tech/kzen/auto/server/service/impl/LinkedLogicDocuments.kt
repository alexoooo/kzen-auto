package tech.kzen.auto.server.service.impl

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.util.digest.Digest


/**
 * Discovers the "linked logic documents" of a runnable document, and combines them into the live-edit
 * migration signal (see [ServerLogicController.pendingMigration]).
 *
 * A hosting object links its callee through an attribute that is `by: Nominal` — a WEAK reference, by design
 * (the callee graph must not join the caller's instantiation) — so the callee never enters the caller's
 * transitive definition closure, and a plain [GraphDefinition.transitiveDigest] over the root document cannot
 * see a callee edit. This widens the SIGNAL while keeping the reference weak: a linked logic document is
 * discovered purely from notation — any object in a visited document with an attribute whose METADATA declares
 * `is: ObjectLocation`, whose value resolves to a location in ANOTHER document whose `main` is a runnable
 * Logic ([AutoConventions.isLogic]). That matches Script `RunStep.instructions`, Flow `RunLogic.instructions`,
 * and Job `RunWorker.instructions` without naming any flavour — a third-party hosting step gets the behaviour
 * for free (no step-type or flavour branch here, per the extensibility rule).
 *
 * Naturally excluded, with no special cases: intra-document `is: ObjectLocation` references (step references,
 * `selfLocation`) target the same document; `is: Map` / `is: List` attributes are not scalar `ObjectLocation`;
 * blank values (archetype defaults) and dangling / unparseable links are skipped — consistent with the
 * controller's "recompile failure falls back to keep-running" policy.
 *
 * Discovery is recursive with a visited-set cycle guard (self-hosting and mutual hosting are legal), and scans
 * each visited document's own notation objects (hosting objects live in the hosting document by construction;
 * archetype documents in the closure carry only blank defaults) — so it stays well-defined even when a
 * document's definition is mid-edit broken.
 */
object LinkedLogicDocuments {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The root document plus every linked logic document reachable from it (recursively).
     */
    fun linkedDocumentPaths(
        graphStructure: GraphStructure,
        rootDocument: DocumentPath
    ): Set<DocumentPath> {
        val graphNotation = graphStructure.graphNotation
        val objectMetadata = graphStructure.graphMetadata.objectMetadata.map

        val objectsByDocument = graphNotation.coalesce.map.entries
            .groupBy { it.key.documentPath }

        val visited = linkedSetOf(rootDocument)
        val open = ArrayDeque<DocumentPath>()
        open.add(rootDocument)

        while (open.isNotEmpty()) {
            val documentPath = open.removeFirst()

            for ((objectLocation, objectNotation) in objectsByDocument[documentPath] ?: continue) {
                val metadata = objectMetadata[objectLocation]
                    ?: continue

                for ((attributeName, attributeMetadata) in metadata.attributes.map) {
                    if (attributeMetadata.type?.className != ObjectLocation.className) {
                        continue
                    }

                    val target = resolveLink(graphStructure, objectNotation.get(attributeName)?.asString())
                        ?: continue

                    if (target.documentPath == documentPath || target.documentPath in visited) {
                        continue
                    }

                    visited.add(target.documentPath)
                    open.add(target.documentPath)
                }
            }
        }

        return visited
    }


    // Best-effort: a blank, unparseable, dangling, or non-logic-document link contributes nothing
    // (the run keeps its current signal rather than failing on a half-typed reference).
    private fun resolveLink(
        graphStructure: GraphStructure,
        attributeValue: String?
    ): ObjectLocation? {
        if (attributeValue.isNullOrBlank()) {
            return null
        }

        return try {
            val graphNotation = graphStructure.graphNotation
            val target = graphNotation.coalesce.locateOptional(ObjectReference.parse(attributeValue))
                ?: return null

            target.takeIf { AutoConventions.isLogic(graphNotation, it.documentPath) }
        }
        catch (_: Exception) {
            null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Content digest over the root document's closure plus each linked logic document's closure — the widened
     * live-edit change signal. Deterministic regardless of discovery order (documents sorted, and each per-doc
     * [GraphDefinition.transitiveDigest] sorts internally); closure overlap between documents (shared archetype
     * documents digested more than once) is harmless.
     */
    fun transitiveDigest(
        graphDefinition: GraphDefinition,
        graphStructure: GraphStructure,
        rootDocument: DocumentPath
    ): Digest {
        val documentPaths = linkedDocumentPaths(graphStructure, rootDocument)

        return Digest.build {
            for (documentPath in documentPaths.sortedBy { it.asString() }) {
                addDigestible(graphDefinition.transitiveDigest(documentPath))
            }
        }
    }
}
