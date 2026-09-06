package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.exec.job.ownership.RunOwnershipControl
import tech.kzen.auto.server.exec.job.ownership.RunOwnershipLedger


/**
 * The run's ownership ledger behind a framework Worker's [JobControl], for the framework's own owner-set
 * propagation (a Formula's non-scalar output inherits its input's owners — E9 item 3). Null outside a run
 * (a test control), where nothing is owned and there is nothing to propagate. Resolved by the control's
 * declared [RunOwnershipControl] capability, never its concrete type (CC-17).
 */
internal fun JobControl.ownership(): RunOwnershipLedger? =
    (this as? RunOwnershipControl)?.ledger
