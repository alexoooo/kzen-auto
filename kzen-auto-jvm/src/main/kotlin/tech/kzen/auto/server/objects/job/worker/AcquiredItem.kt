package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.exec.engine.resource.Hold
import tech.kzen.lib.common.exec.engine.resource.ResourceOwners


/**
 * One item a framework pull loop acquired inside the blocking boundary (E9 item 2, source ingress): the native
 * to lift (a `Borrowed` already unwrapped), the resources the lifted value depends on, and the producer hold
 * taken at the pull, kept through lift, conversion, projection and send. [release] with no channel hold taken —
 * the lift failed, the source elected not to emit, or the pull was cancelled before the send — closes the item;
 * after a send the channel holds it.
 */
class AcquiredItem internal constructor(
    val native: Any?,
    private val owners: ResourceOwners,
    private val hold: Hold
) {
    /** Whether the run adopted this item (an unowned scalar or a Borrowed native costs nothing). */
    val owned: Boolean
        get() = hold.isActive


    /** Lifts the native with [metadata], carrying the owners the run attached at the pull. */
    fun lift(contract: DataContract?, metadata: ValueMetadata? = null): DataValue =
        JobDataValues.lift(native, contract).withMetadata(metadata).withOwners(owners)


    /** Lets go of the producer hold; idempotent. */
    fun release() {
        hold.release()
    }
}
