package tech.kzen.auto.server.objects.job.service

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.util.digest.Digest
import java.nio.file.Files
import java.nio.file.Path


/**
 * Owns the per-run, per-Worker scratch directories that file-backed Job Workers (Pivot, Explore) use for their
 * on-disk engine state (an H2-backed [tech.kzen.auto.server.objects.report.exec.output.pivot.PivotBuilder], an
 * [tech.kzen.auto.server.objects.report.exec.output.flat.IndexedCsvTable]). The Job analogue of
 * [tech.kzen.auto.server.objects.report.service.ReportWorkPool] — but a Job has no persisted run manifest: a
 * scratch dir is pure transient compute state, deleted when the run settles, never resumed from disk.
 *
 * Layout: `<work>/job/<run-digest>/<worker-digest>`, keyed on
 * - the migrate-stable [LogicRunId] — so a Worker rebuilt across a live edit resolves the SAME run dir (the P4
 *   baseline lets a file-backed operator restart on an edit, which is coherent only because the path is stable);
 * - the Worker's [ObjectStableId] — so sibling Workers of one run never collide, and the same identity survives
 *   the rebuild.
 *
 * A scratch dir is created lazily on the Worker's first [tech.kzen.auto.common.paradigm.job.control.JobControl.scratchDir]
 * call ([JobRun][tech.kzen.auto.server.exec.job.JobRun] resolves the path up front but does not touch the disk),
 * so a run whose Workers never need one leaves no directory behind. Cleanup is layered: each stateful Worker's
 * `onClose` closes-then-deletes its own store (an H2 file holds a Windows lock, so close must precede delete),
 * [JobRun] registers a run-root [tech.kzen.lib.common.exec.engine.ClosePolicy.Auto] resource that sweeps the whole
 * run dir when the run settles, and this pool's boot-time sweep clears any tree a hard-killed prior process leaked.
 */
class JobWorkPool(
    private val workUtils: WorkUtils
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(JobWorkPool::class.java)
        private val jobDir = Path.of("job")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val base: Path = workUtils.resolve(jobDir).toAbsolutePath().normalize()


    init {
        // Boot sweep: a Job scratch dir holds only in-run transient state, so anything already under `base` is
        // leaked by a prior (now-dead) process — clear it so a hard kill can't accumulate garbage. A live run of
        // THIS process recreates its own dirs lazily on first scratchDir() call.
        if (Files.exists(base)) {
            try {
                WorkUtils.recursivelyDeleteDir(base)
            }
            catch (e: Exception) {
                logger.warn("Unable to sweep stale Job scratch dirs: {}", base, e)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The (not-yet-created) scratch directory for the Worker [workerStableId] of run [runId]. Deterministic:
     * a live-edit rebuild of the same Worker resolves the identical path.
     */
    fun workerScratchDir(runId: LogicRunId, workerStableId: ObjectStableId): Path {
        return runDir(runId).resolve(WorkUtils.filenameEncodeDigest(Digest.ofUtf8(workerStableId.value)))
    }


    /**
     * Delete the entire scratch tree of run [runId] (all its Workers' dirs). Idempotent — a no-op if the run
     * never created a scratch dir. Called from [JobRun]'s run-root Auto resource when the run settles.
     */
    fun deleteRun(runId: LogicRunId) {
        val runDir = runDir(runId)
        if (!Files.exists(runDir)) {
            return
        }
        try {
            WorkUtils.recursivelyDeleteDir(runDir)
        }
        catch (e: Exception) {
            logger.warn("Unable to delete Job run scratch dir: {}", runDir, e)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runDir(runId: LogicRunId): Path {
        return base.resolve(WorkUtils.filenameEncodeDigest(Digest.ofUtf8(runId.value)))
    }
}
