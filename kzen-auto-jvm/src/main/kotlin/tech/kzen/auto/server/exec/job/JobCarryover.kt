package tech.kzen.auto.server.exec.job

import tech.kzen.auto.server.objects.job.channel.ChannelCarryover
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * What the Job's root node carries across a live-edit migration: each stream channel's in-flight elements (by
 * stable id, with their channel holds). The run's resource registry itself is the engine's and outlives the
 * graph instance; every carried element is still held by its channel. Closed by the engine only when no rebuilt
 * root claims it (the run is not rebuilt as a Job), in which case every carried hold is released.
 */
class JobCarryover(
    val channels: Map<ObjectStableId, ChannelCarryover>,
    /** When the run started ([System.nanoTime]): the Workers' progress clock, kept across the edit. */
    val startedNanos: Long
): AutoCloseable {
    override fun close() {
        var failure: Throwable? = null
        for (carryover in channels.values) {
            try {
                carryover.release()
            }
            catch (e: Exception) {
                if (failure == null) failure = e else failure.addSuppressed(e)
            }
        }
        failure?.let { throw it }
    }
}
