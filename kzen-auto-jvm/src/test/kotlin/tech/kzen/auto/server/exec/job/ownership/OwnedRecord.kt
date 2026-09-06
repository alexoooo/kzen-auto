package tech.kzen.auto.server.exec.job.ownership


/**
 * A closeable element whose readable properties are all scalars, so a Result boundary can snapshot it
 * structurally (`name`, `value`, `closes`) — what a snapshot of an arena-backed record looks like.
 */
class OwnedRecord(
    val name: String,
    val value: Int
): AutoCloseable {
    @Volatile
    var closes: Int = 0
        private set


    override fun close() {
        closes += 1
    }


    override fun toString(): String = "OwnedRecord($name=$value, closes=$closes)"
}
