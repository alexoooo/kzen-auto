package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.auto.server.exec.job.ownership.OwnerSet
import tech.kzen.auto.server.exec.job.ownership.RunOwnershipLedger
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * One item a framework pull loop acquired inside the blocking boundary (E9 item 2, source ingress): the native
 * to lift (a `Borrowed` already unwrapped) and the producer hold taken at the pull, kept through lift,
 * conversion, projection and send. [release] with no channel hold taken — the lift failed, the source elected
 * not to emit, or the pull was cancelled before the send — closes the item; after a send the channel holds it.
 */
class AcquiredItem internal constructor(
    val native: Any?,
    private val owners: OwnerSet,
    private val hold: ValueLease
) {
    /** Whether the run adopted this item (an unowned scalar or a Borrowed native costs nothing). */
    val owned: Boolean
        get() = hold.isActive


    /** Lifts the native, carrying the owners the run attached at the pull. */
    fun lift(ledger: RunOwnershipLedger?, contract: DataContract?): DataValue {
        val value = JobDataValues.lift(native, contract)
        if (ledger != null && !owners.isEmpty) {
            ledger.attach(value, owners)
        }
        return value
    }


    /** Lets go of the producer hold; idempotent. */
    fun release() {
        hold.release()
    }
}
