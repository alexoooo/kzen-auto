package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.server.objects.job.worker.WorkerBase
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import java.nio.file.Path


/**
 * One Job [Worker] as an engine [Logic], so a Job hosts each Worker as its own confined node ([Execution.host])
 * — giving every Worker independent checkpoint parking, its own trace scope, and a place in the run tree. A
 * Worker's only framework contact is [tech.kzen.auto.common.paradigm.job.control.JobControl], so the whole
 * Worker SPI (WorkerBase / Source / Transform / Sink, channels) is reused unchanged: this just adapts the node's
 * [Execution] into an [EngineJobControl] and runs the Worker to completion. A Worker has no Logic output — it
 * communicates over channels — so the result is the empty tuple.
 *
 * LIVE-EDIT MIGRATION (logic-spec §5): a Worker's run-scoped state IS this node's migratable state, so the two
 * are bridged directly onto the node's [Execution] capture/restore contract (the engine carries it by the
 * Worker's [stable id][tech.kzen.lib.common.service.store.normal.ObjectStableId] — see [JobRun]):
 * - [Execution.restored] (the state the same-stable-id Worker of the edited definition captured) is adopted via
 *   [WorkerBase.loadMigrationState] BEFORE the Worker runs, so it resumes from a carried accumulator / open
 *   reader instead of restarting.
 * - [Execution.onCapture] hands back [WorkerBase.captureMigrationState], which the engine invokes at the
 *   quiescent migration barrier BEFORE teardown — letting a Worker DETACH a live handle (an open file) so the
 *   torn-down instance's `onClose` skips it and the rebuilt instance adopts it.
 *
 * A raw (non-[WorkerBase]) [Worker] opts into neither, so it cleanly restarts on an edit — the safe §5 default.
 *
 * PAUSE-ON-ERROR (logic-spec §4): the Worker's whole run is wrapped in [Execution.recoverable], so a Worker
 * throwing (an expression error, a nested child's terminal failure) parks Suspended(Error) for inspect / fix +
 * resume when pause-on-error is on, and fails the run when it is off — uniform across ALL Workers, exactly as a
 * Script step / Flow vertex. A nested-Logic [RunWorker]'s child that halts (a Pause step, or a step parked
 * under pause-on-error) does NOT surface here: its host call stays suspended and the child's own recoverable
 * boundary parks it, bringing the whole Job to a quiescent wavefront centrally.
 */
class WorkerLogic(
    private val worker: Worker,
    private val childLogicHost: JobChildLogicHost,
    private val objectStableMapper: ObjectStableMapper,
    private val scratchDir: Path
): Logic {
    override fun signature(): LogicSignature {
        return LogicSignature.empty
    }


    override suspend fun run(execution: Execution): TupleValue {
        val workerBase = worker as? WorkerBase
        if (workerBase != null) {
            execution.restored?.let { workerBase.loadMigrationState(it) }
            execution.onCapture { workerBase.captureMigrationState() }
        }

        val control = EngineJobControl(execution, childLogicHost, objectStableMapper, scratchDir)

        // The engine renders the failure (the run settles / parks per pause-on-error); the run-level ErrorPaused
        // state already surfaces which Worker halted (per-Worker error chips are a separate display gap).
        execution.recoverable({ }) {
            worker.run(control)
        }
        return TupleValue.empty
    }
}
