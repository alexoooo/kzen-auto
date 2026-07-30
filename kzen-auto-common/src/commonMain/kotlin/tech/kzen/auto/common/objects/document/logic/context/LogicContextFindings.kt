package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.lib.common.model.obj.ObjectPath


/**
 * What [LogicContextAnalysis] found in one document, split by severity because the two have different
 * consequences: an error disables Run, a warning is advisory and never gates anything.
 *
 * Both maps are keyed by the object path the finding attaches to — a step, or `main` for a document-level
 * declaration problem. At most one entry per object per severity: several findings on one object are joined
 * into a single message, because the surfaces that render them (a step's error indicator, the signature
 * editor) have room for one.
 */
data class LogicContextFindings(
    val errors: Map<ObjectPath, String>,
    val warnings: Map<ObjectPath, String>
) {
    companion object {
        val empty = LogicContextFindings(mapOf(), mapOf())
    }
}
