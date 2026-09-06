package tech.kzen.auto.server.exec.job.ownership


/**
 * Who holds a lease on an owned native: a channel, a Worker's in-flight callback, an accumulator's explicit
 * retention, or the producer between pull and send. Named so the ledger can say *who* keeps a resource open
 * (E9 item 5) — a Worker's holder is its notation location, never its instance, so a live-edit replacement is
 * the same holder.
 */
data class LeaseHolder(
    val name: String
) {
    companion object {
        /** The framework's pull loop, from acquisition through lift, conversion and send. */
        val producer = LeaseHolder("producer")
    }

    override fun toString(): String = name
}
