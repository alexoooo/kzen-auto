package tech.kzen.auto.server.objects.job.worker


/**
 * An element a source lends rather than gives (docs/plans/2026-09-16_borrowed-elements.md §3.1): the run
 * adopts it at the pull like any closeable, its holds follow the channel and callback leases, and the source
 * waits for the last release — which is the [close] that invalidates it — before it advances. Because that
 * wait must end, a lent element can never be kept past a callback:
 * [tech.kzen.auto.common.paradigm.job.control.JobControl.retain] and `yieldResult` refuse it by name. A value
 * derived from it inherits its owners and is lent the same way; a value with no owners (a lifted literal row, a
 * `Written` record, a snapshot) is independent.
 */
interface LentElement: AutoCloseable {
    /** The element by name for a refusal, e.g. `entry 'foo.txt'`. */
    fun lentName(): String

    /** The lender by name, e.g. `'input.tar.gz'`. */
    fun lender(): String
}
