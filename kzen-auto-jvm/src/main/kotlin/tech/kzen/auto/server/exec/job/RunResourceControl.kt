package tech.kzen.auto.server.exec.job

import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.engine.resource.Hold
import tech.kzen.lib.common.exec.engine.resource.ResourceRegistry


/**
 * The run's resources behind a Worker's [tech.kzen.auto.common.paradigm.job.control.JobControl], for the
 * framework's own holders: the source ingress adopting what it pulls, a drive loop's per-callback hold. A Worker
 * never sees this capability — it only reaches the [Hold]s the SPI hands it — and the framework resolves it by
 * capability, never by the control's concrete type (CC-17).
 */
interface RunResourceControl {
    /** The run's registry: every resource adopted in this run, by native identity, with its holds by holder. */
    val resources: ResourceRegistry


    /**
     * The framework's hold on [value] for the duration of one callback, named by this Worker: taken before the
     * channel's hold is released, so the count never touches zero between holders, and released when the callback
     * returns. Unlike [tech.kzen.auto.common.paradigm.job.control.JobControl.retain] never refused for a lent
     * element — the callback is exactly the scope a lent element is valid in.
     */
    fun holdForCallback(value: DataValue): Hold
}
