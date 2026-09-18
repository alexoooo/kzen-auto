package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * The capability a run-bound `JobControl` declares so the framework's own Workers can reach the run's ledger
 * (owner-set propagation, E9 item 3) without naming the control's concrete type. Not a Worker SPI: an author's
 * only ownership cooperation is `JobControl.retain`, which refuses a lent element by name
 * (docs/plans/2026-09-16_borrowed-elements.md §3.3); the framework's own per-callback hold on the same
 * element goes through [holdForCallback], which does not.
 */
interface RunOwnershipControl {
    val ledger: RunOwnershipLedger

    /** The framework drive loop's hold on an element for the duration of its callback, named by the Worker. */
    fun holdForCallback(value: DataValue): ValueLease
}
