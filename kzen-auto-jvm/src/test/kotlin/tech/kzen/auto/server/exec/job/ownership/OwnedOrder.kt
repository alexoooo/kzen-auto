package tech.kzen.auto.server.exec.job.ownership


/**
 * An arena-backed order (what the in-process host's `SymbolDay` route looks like at a Job): a closeable
 * root whose executions are plain records — the E8 projection reads them while the run holds the order and
 * emits rows that must stay valid after the order closed.
 */
class OwnedOrder(
    val symbol: String,
    val executions: List<Execution>
): AutoCloseable {
    data class Execution(val price: Double, val qty: Long)


    @Volatile
    var closes: Int = 0
        private set


    override fun close() {
        closes += 1
    }


    fun notional(): Double = executions.sumOf { it.price * it.qty }
}
