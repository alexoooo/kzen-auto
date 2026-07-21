package tech.kzen.auto.server.service.impl

import tech.kzen.auto.common.paradigm.logic.LogicCallGraph
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.util.digest.Digest


/**
 * Combines a runnable document with its "linked logic documents" — the callees its hosting objects reach, per
 * [LogicCallGraph] — into the live-edit migration signal (see [ServerLogicController.pendingMigration]).
 *
 * A hosting object links its callee through an attribute that is `by: Nominal` — a WEAK reference, by design
 * (the callee graph must not join the caller's instantiation) — so the callee never enters the caller's
 * transitive definition closure, and a plain [GraphDefinition.transitiveDigest] over the root document cannot
 * see a callee edit. This widens the SIGNAL while keeping the reference weak.
 *
 * Discovery being best-effort (a dangling or half-typed link contributes no edge) is consistent with the
 * controller's "recompile failure falls back to keep-running" policy.
 */
object LinkedLogicDocuments {
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
        val documentPaths = LogicCallGraph.transitiveCallees(graphStructure, rootDocument) + rootDocument

        return Digest.build {
            for (documentPath in documentPaths.sortedBy { it.asString() }) {
                addDigestible(graphDefinition.transitiveDigest(documentPath))
            }
        }
    }
}
