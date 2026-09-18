package tech.kzen.auto.server.objects.job.worker


/**
 * A source that lends its elements (docs/plans/2026-09-16_borrowed-elements.md §3.5): an element it emits is
 * valid only until it advances, so it advances only once the element's last hold is released. The run's
 * consumers downstream of such a source keep draining while it is [lending]: their
 * [tech.kzen.auto.common.paradigm.job.control.JobControl.checkpoint] does not park until the lent element is
 * released, so a bounded output channel can never hold the source inside a lent element when the run is
 * asked to pause for a live edit, and the source itself never parks while lending. Read from other Workers'
 * coroutines and from the deadlock monitor's thread.
 */
interface BorrowingSource {
    /** True from the emit of a lent element until its release. */
    fun lending(): Boolean

    /**
     * True while suspended waiting for the lent element's release: the source can make no progress on its own,
     * so the deadlock monitor counts it as blocked alongside the channel-parked Workers.
     */
    fun awaitingRelease(): Boolean
}
