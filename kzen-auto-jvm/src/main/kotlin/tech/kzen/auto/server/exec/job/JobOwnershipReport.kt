package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.exec.job.ownership.RunOwnershipLedger
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * The run's ownership diagnostics (E9 item 5), emitted on the Job's ROOT node at [address]: the live holds
 * aggregated by holder name (a channel, a Worker's callback, an accumulator's explicit lease, the producer),
 * each stream channel's occupancy, how many natives the run has adopted and closed, and — when the run's
 * progress clock has stalled — a warning naming the holders. Aggregates only: per-item detail stays bounded
 * and on demand ([RunOwnershipLedger.describeLive]) so a status publication never carries a live-resource list.
 */
internal class JobOwnershipReport(
    private val execution: Execution,
    private val ledger: RunOwnershipLedger,
    private val channels: Map<ObjectLocation, JobChannel>
) {
    companion object {
        /** Reserved emit address of the run-level ownership report, distinct from any Worker's progress. */
        const val addressMarker = "\$job-ownership"
    }


    private val address = Address.of(addressMarker)


    fun emit(stalled: Boolean) {
        val holds = ledger.holdsByHolder().entries
            .sortedBy { it.key.name }
            .associate { (holder, count) -> holder.name to count.toLong() }
        val occupancy = channels.entries.associate { (location, channel) ->
            location.objectPath.asString() to channel.queuedElements().toLong()
        }
        val report = linkedMapOf<String, Any?>(
            JobConventions.ownershipHoldsKey to holds,
            JobConventions.ownershipQueuedKey to occupancy,
            JobConventions.ownershipLiveKey to ledger.liveCount().toLong(),
            JobConventions.ownershipClosedKey to ledger.closedCount().toLong())
        if (stalled) {
            report[JobConventions.ownershipStalledKey] = true
        }
        execution.emit(address, ExecutionValue.of(report))
    }


    /** The stall warning's text: who holds what, with a bounded sample of the live natives. */
    fun stallMessage(): String {
        val holders = ledger.holdsByHolder().entries
            .sortedByDescending { it.value }
            .joinToString { (holder, count) -> "${holder.name}: $count" }
        val sample = ledger.describeLive(JobConventions.ownershipDetailLimit).joinToString()
        return "No progress while owned natives are held — $holders; live: $sample"
    }
}
