package tech.kzen.auto.server.exec.job

import tech.kzen.auto.server.exec.job.ownership.RunOwnershipLedger
import tech.kzen.auto.server.objects.job.channel.ChannelCarryover
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * What the Job's root node carries across a live-edit migration: each stream channel's in-flight elements (by
 * stable id, with their channel leases) and the run's ownership ledger, which outlives the graph instance —
 * the run id is migrate-stable and every carried element is still held by its channel or Worker. Closed by the
 * engine only when no rebuilt root claims it (the run is not rebuilt as a Job), in which case every owned
 * native is closed as at teardown.
 */
class JobCarryover(
    val channels: Map<ObjectStableId, ChannelCarryover>,
    val ledger: RunOwnershipLedger
): AutoCloseable {
    override fun close() {
        ledger.closeAll(null)?.let { throw it }
    }
}
