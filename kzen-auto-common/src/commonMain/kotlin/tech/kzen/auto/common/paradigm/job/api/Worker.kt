package tech.kzen.auto.common.paradigm.job.api

import tech.kzen.auto.common.paradigm.job.control.JobControl


/**
 * A Job's unit of concurrent work. Unlike a Flow vertex (driven one step at a time by the executor), a
 * Worker owns its own run loop: [run] executes to completion on its own coroutine, communicating
 * exclusively through Channel endpoints injected as constructor attributes (notation `in` / `out`).
 *
 * The only framework service passed to [run] is [JobControl] — call [JobControl.checkpoint] at message
 * boundaries (and inside long compute loops) so the Job stays cooperatively pausable / cancellable.
 */
interface Worker {
    suspend fun run(control: JobControl)
}
