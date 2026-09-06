package tech.kzen.auto.server.objects.job.channel


/**
 * The framework-facing side of a [JobChannel] producer endpoint: whether a flush since the last query parked
 * on a full channel. A Worker unparked by a migration's channel drain must re-park at a checkpoint before it
 * produces anything more (the drain would otherwise miss it), so the [Emitter][tech.kzen.auto.server.objects.job.worker.Emitter]
 * checkpoints right after a flush that parked. Not part of the Worker SPI.
 */
internal interface FrameworkChannelOutput {
    /** True once per park: whether a flush suspended on backpressure since the previous call. */
    fun takeParked(): Boolean
}
