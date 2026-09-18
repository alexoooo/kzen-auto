package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.auto.server.exec.job.ownership.RunOwnershipControl
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * The framework drive loops' per-callback hold on an owned element (E9 item 2): a Worker is done with an
 * element when its callback returns, not when the element left the channel. The hold is taken through
 * [RunOwnershipControl.holdForCallback] — named by the Worker's location, and unlike [JobControl.retain] never
 * refused for a lent element — before the channel's hold is released, so the count never touches zero between
 * holders, and released when the callback returns, on success or failure alike; a close failure on that last
 * release becomes the callback's outcome, or rides as suppressed on the callback's own failure. An unowned
 * element costs a no-op lease.
 */
internal object CallbackLeases {
    /** Transform / Sink: the channel's hold becomes the callback's (released once the callback holds). */
    suspend fun transferring(
        control: JobControl,
        element: DataValue,
        channelLease: ValueLease?,
        callback: suspend () -> Unit
    ) {
        val callbackLease = if (channelLease != null) hold(control, element) else ValueLease.none
        channelLease?.release()
        holdingLease(callbackLease, callback)
    }


    /** Expanding transform: the callback holds alongside the channel, which holds until the expansion completed. */
    suspend fun holding(control: JobControl, element: DataValue, callback: suspend () -> Unit) {
        holdingLease(hold(control, element), callback)
    }


    private fun hold(control: JobControl, element: DataValue): ValueLease =
        (control as? RunOwnershipControl)?.holdForCallback(element) ?: control.retain(element)


    private suspend fun holdingLease(lease: ValueLease, callback: suspend () -> Unit) {
        var failure: Throwable? = null
        try {
            callback()
        }
        catch (e: Throwable) {
            failure = e
            throw e
        }
        finally {
            try {
                lease.release()
            }
            catch (e: Exception) {
                if (failure == null) throw e else failure.addSuppressed(e)
            }
        }
    }
}
