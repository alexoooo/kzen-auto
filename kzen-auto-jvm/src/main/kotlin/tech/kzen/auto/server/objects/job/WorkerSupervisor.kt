package tech.kzen.auto.server.objects.job

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.common.paradigm.job.control.JobControl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


/**
 * Owns the [CoroutineScope] + [CountingDispatcher] a Job's Workers run on, and exposes the quiescence
 * primitives the [JobExecution] poll-and-await loop reads. A [SupervisorJob] keeps one Worker's failure
 * from cancelling its siblings (the failure is recorded and surfaced when the run settles); cancellation
 * tears the whole scope down and joins.
 */
class WorkerSupervisor(
    parallelism: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val dispatcher = CountingDispatcher(parallelism)
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + supervisorJob)

    private val workerJobs = mutableListOf<Job>()
    private val failures = ConcurrentLinkedQueue<String>()

    // Each launched Worker's terminal status (label -> "done" / "failed: <reason>"), so [JobExecution]
    // can surface it on the Worker's trace when the run settles — the Job panel's only run feedback.
    private val outcomes = ConcurrentHashMap<String, String>()


    //-----------------------------------------------------------------------------------------------------------------
    fun launch(worker: Worker, control: JobControl, label: String) {
        val job = scope.launch {
            try {
                worker.run(control)
                outcomes[label] = "done"
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (t: Throwable) {
                val message = t.message ?: t::class.simpleName ?: "failed"
                failures.add("$label: $message")
                outcomes[label] = "failed: $message"
            }
        }
        workerJobs.add(job)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun awaitQuiescent() {
        dispatcher.awaitQuiescent()
    }


    fun awaitQuiescenceOrProgress(timeoutMillis: Long): Boolean {
        return dispatcher.awaitQuiescenceOrProgress(timeoutMillis)
    }


    fun isQuiescent(): Boolean {
        return dispatcher.isQuiescent()
    }


    fun allTerminated(): Boolean {
        return workerJobs.all { it.isCompleted }
    }


    fun firstFailure(): String? {
        return failures.peek()
    }


    // The terminal status of the Worker launched under [label], or null if it is still running.
    fun outcome(label: String): String? {
        return outcomes[label]
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun cancelAndJoin() {
        scope.cancel()
        // Join while the dispatcher is still alive — cancelled workers resume (with CancellationException)
        // on its threads to unwind — then shut the pool down.
        runBlocking {
            supervisorJob.join()
        }
        dispatcher.shutdown()
    }


    fun shutdown() {
        dispatcher.shutdown()
    }
}
