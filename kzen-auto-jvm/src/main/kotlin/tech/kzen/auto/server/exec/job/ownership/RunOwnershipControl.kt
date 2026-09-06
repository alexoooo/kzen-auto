package tech.kzen.auto.server.exec.job.ownership


/**
 * The capability a run-bound `JobControl` declares so the framework's own Workers can reach the run's ledger
 * (owner-set propagation, E9 item 3) without naming the control's concrete type. Not a Worker SPI: an author's
 * only ownership cooperation is `JobControl.retain`.
 */
interface RunOwnershipControl {
    val ledger: RunOwnershipLedger
}
