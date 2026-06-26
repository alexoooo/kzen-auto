package tech.kzen.auto.server.service.impl

import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Narrow seam for a DETACHED nested Logic to appear in the run's frame tree (the one [ServerLogicController]
 * builds) for VISIBILITY ONLY: the sidebar's executing-highlight + stack-depth badge and the auto-follow
 * navigation are all derived from that tree (client `LogicRunFrames`), so a Logic absent from it shows no
 * "executing" state regardless of its trace.
 *
 * The shared-control [tech.kzen.lib.common.exec.logic.LogicHandle] path grows the tree as a side effect of
 * starting a nested Logic on the run's single steppable control. A Job's Run Worker children deliberately do
 * NOT take that path — they run full-speed on their own private controls (confinement, so the concurrently
 * running Workers of a Job don't share a steppable control; see
 * [tech.kzen.auto.server.objects.job.JobLogicHostImpl]). They register here instead, so they become visible
 * recursively without joining the step spine. Stepping INTO such a child is intentionally out of scope: the
 * host drives the child itself, and this only mirrors its presence into the tree.
 */
interface NestedFrameRegistry {
    /**
     * Attach [execution] as a child frame of the frame identified by [hostExecutionId] (the caller's
     * execution id — the Job's own for a top-level Run Worker child, or the deeper caller's for a further
     * nested child), labelled by [location] and keyed by [executionId], so it shows as executing at the
     * right depth. Returns a handle whose [AutoCloseable.close] detaches the frame; close it when the child
     * finishes. A no-op handle is returned when there is no active run or no host frame matches (e.g. the
     * Logic is running outside a tracked run), so callers need no run-state check of their own.
     */
    fun attach(
        hostExecutionId: LogicExecutionId,
        location: ObjectLocation,
        executionId: LogicExecutionId,
        execution: LogicExecution
    ): AutoCloseable
}
