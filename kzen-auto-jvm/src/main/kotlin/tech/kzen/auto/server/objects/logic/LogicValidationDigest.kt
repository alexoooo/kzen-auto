package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.server.service.impl.LinkedLogicDocuments
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.util.digest.Digest


/**
 * The cache key a per-document validation pass is stored under (shared by the Script and Job validation
 * caches, so the key semantics cannot drift): a content digest over everything validation reads — the root
 * document's transitive closure (which per [tech.kzen.lib.common.model.definition.GraphDefinition.transitiveDigest]
 * also covers the document's pruned-by-design members — a Job's Workers, dropped from the definition by their
 * blank channel ports yet digested from notation — and member order) PLUS each linked logic document's closure
 * ([LinkedLogicDocuments.transitiveDigest] — a hosting object's value type comes from its weakly-linked
 * callee's signature, invisible to a plain per-document closure digest).
 *
 * Null when a mid-edit broken graph makes the closure digest uncomputable — the caller then computes
 * uncached (matching the logic controller's keep-running fallback).
 */
object LogicValidationDigest {
    fun documentClosureKey(
        documentPath: DocumentPath,
        graphDefinition: GraphDefinition
    ): Digest? {
        return try {
            LinkedLogicDocuments.transitiveDigest(
                graphDefinition, graphDefinition.graphStructure, documentPath)
        }
        catch (_: Exception) {
            null
        }
    }
}
