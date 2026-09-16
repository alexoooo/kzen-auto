package tech.kzen.auto.server.objects.job.worker.content.scope


/**
 * A Worker that owns a scope over a stream of entries and quiesces only at an entry boundary (design §6.7,
 * spike CS3). The run's consumers downstream of such a Worker keep draining while it is [insideEntry]: their
 * [tech.kzen.auto.common.paradigm.job.control.JobControl.checkpoint] does not park until the scope has reached
 * its boundary, so a bounded output channel can never hold the scope inside an entry when the run is asked to
 * pause for a live edit. Read from other Workers' coroutines.
 */
interface ScopeBoundary {
    fun insideEntry(): Boolean
}
