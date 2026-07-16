package tech.kzen.auto.client.objects.document.job

import tech.kzen.lib.common.exec.engine.OutcomeTrace


/**
 * A settled Worker's terminal outcome, for the Job UI outcome chip. This is a GENERAL per-node fact — every
 * Worker (and the run root) settles with exactly one outcome — not a Worker-type-specific payload, so it lives
 * as a typed field beside [JobWorkerProgress.status] rather than in the opaque progress map, and needs no
 * per-Worker branching to render. Parsed from the flavour-neutral node-outcome trace value the server projects
 * (see [OutcomeTrace] / [LogicTracePath.nodeOutcome][tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath.nodeOutcome]).
 */
data class WorkerOutcome(
    val kind: Kind,
    val message: String?
) {
    enum class Kind { Success, Failed, Cancelled }


    companion object {
        fun ofTraceValue(raw: Any?): WorkerOutcome? {
            val map = raw as? Map<*, *>
                ?: return null

            val kind = when (map[OutcomeTrace.kindKey] as? String) {
                OutcomeTrace.kindSuccess -> Kind.Success
                OutcomeTrace.kindFailed -> Kind.Failed
                OutcomeTrace.kindCancelled -> Kind.Cancelled
                else -> return null
            }

            return WorkerOutcome(kind, map[OutcomeTrace.messageKey] as? String)
        }
    }
}
