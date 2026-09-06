package tech.kzen.auto.server.exec.job.ownership

import java.util.concurrent.atomic.AtomicInteger


/**
 * A closeable with no readable property: it describes as an opaque native, so an owned one cannot be
 * snapshotted at a Result boundary and must fail by name there.
 */
class OpaqueHandle: AutoCloseable {
    companion object {
        val closes = AtomicInteger()
    }


    override fun close() {
        closes.incrementAndGet()
    }
}
