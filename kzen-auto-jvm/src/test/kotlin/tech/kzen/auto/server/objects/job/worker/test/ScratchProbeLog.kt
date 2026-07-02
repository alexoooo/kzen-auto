package tech.kzen.auto.server.objects.job.worker.test

import tech.kzen.auto.common.paradigm.job.control.JobControl
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap


/**
 * Shared static recorder for the scratch-dir probe Workers ([ScratchProbeSourceWorker] / [ScratchProbeSinkWorker])
 * driven by [tech.kzen.auto.server.exec.job.JobScratchDirTest]. Each probing Worker records its resolved
 * [JobControl.scratchDir] path here, keyed by Worker name, so the test can assert the two Workers got isolated
 * dirs and that both were swept once the run settled.
 *
 * [probe] also WRITES a marker file into the dir — proving the dir was really created and writable at probe time:
 * if [JobControl.scratchDir] had handed back a non-existent path the write would throw, failing the Worker (and
 * so the run), so a Success outcome already witnesses "the dir existed during the run". Static + thread-safe
 * ([ConcurrentHashMap]) so it survives the concurrent Worker coroutines; [reset] before each test.
 */
object ScratchProbeLog {
    private val pathByWorker = ConcurrentHashMap<String, String>()


    suspend fun probe(control: JobControl, workerName: String) {
        val dir = control.scratchDir()
        control.runBlockingIo {
            Files.writeString(Path.of(dir, "marker.txt"), workerName)
        }
        pathByWorker[workerName] = dir
    }


    fun reset() {
        pathByWorker.clear()
    }


    fun snapshot(): Map<String, String> {
        return pathByWorker.toMap()
    }
}
