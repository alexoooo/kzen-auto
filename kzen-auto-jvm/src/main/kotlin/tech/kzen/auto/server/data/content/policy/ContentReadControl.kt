package tech.kzen.auto.server.data.content.policy

import java.util.concurrent.CancellationException


/** One content read's cancellation check, and the source bytes it has taken. */
class ContentReadControl {
    /** Bytes taken from the source beneath any coding, counted by `SourceByteCountingContent`. */
    var sourceBytesRead: Long = 0
        private set


    fun checkpoint() {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Content read interrupted")
        }
    }


    fun recordSourceBytes(count: Int) {
        require(count >= 0) { "Source byte count must not be negative" }
        sourceBytesRead += count
    }
}
