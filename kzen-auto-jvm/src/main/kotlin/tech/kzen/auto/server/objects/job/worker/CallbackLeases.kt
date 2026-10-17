package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.exec.job.RunResourceControl
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.engine.resource.Hold


/**
 * The framework drive loops' per-callback hold on an owned element (E9 item 2): a Worker is done with an
 * element when its callback returns, not when the element left the channel. The hold is taken through
 * [RunResourceControl.holdForCallback] — named by the Worker's location, and unlike [JobControl.retain] never
 * refused for a lent element — before the channel's hold is released, so the count never touches zero between
 * holders, and released when the callback returns, on success or failure alike; a close failure on that last
 * release becomes the callback's outcome, or rides as suppressed on the callback's own failure. An unowned
 * element costs a no-op hold.
 */
internal object CallbackLeases {
    /** Transform / Sink: the channel's hold becomes the callback's (released once the callback holds). */
    suspend fun transferring(
        control: JobControl,
        element: DataValue,
        channelHold: Hold?,
        callback: suspend () -> Unit
    ) {
        val callbackHold = if (channelHold != null) hold(control, element) else Hold.none
        channelHold?.release()
        holdingHold(callbackHold, callback)
    }


    /** Expanding transform: the callback holds alongside the channel, which holds until the expansion completed. */
    suspend fun holding(control: JobControl, element: DataValue, callback: suspend () -> Unit) {
        holdingHold(hold(control, element), callback)
    }


    private fun hold(control: JobControl, element: DataValue): Hold =
        (control as? RunResourceControl)?.holdForCallback(element) ?: control.retain(element)


    private suspend fun holdingHold(hold: Hold, callback: suspend () -> Unit) {
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
                hold.release()
            }
            catch (e: Exception) {
                if (failure == null) throw e else failure.addSuppressed(e)
            }
        }
    }
}
