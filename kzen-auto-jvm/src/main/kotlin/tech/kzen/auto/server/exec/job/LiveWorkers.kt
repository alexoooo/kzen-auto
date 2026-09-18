package tech.kzen.auto.server.exec.job

import tech.kzen.auto.server.objects.job.worker.WorkerBase
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import java.util.concurrent.CopyOnWriteArrayList


/**
 * The Workers of the runs currently hosted from one [JobLogic] — usually one run; a Job nested in a Script is
 * hosted once per parent element — keyed by stable id, so [JobLogic.refuseMigration] judges a live edit with the
 * instances that actually hold run-scoped state ([WorkerBase.migrationKey]) and no general layer learns a Worker
 * type (borrowed elements §3.6, docs/plans/2026-09-16_borrowed-elements.md). A run registers its Workers once
 * they are instantiated and withdraws them when it ends, including at its own migration barrier — the rebuilt
 * run registers its own.
 */
internal class LiveWorkers {
    class LiveWorker(
        val location: ObjectLocation,
        val worker: WorkerBase
    )


    private val runs = CopyOnWriteArrayList<Map<ObjectStableId, LiveWorker>>()


    fun register(workers: Map<ObjectStableId, LiveWorker>): AutoCloseable {
        runs.add(workers)
        return AutoCloseable { runs.remove(workers) }
    }


    fun forEach(action: (ObjectStableId, LiveWorker) -> Unit) {
        for (run in runs) {
            for ((stableId, worker) in run) {
                action(stableId, worker)
            }
        }
    }
}
