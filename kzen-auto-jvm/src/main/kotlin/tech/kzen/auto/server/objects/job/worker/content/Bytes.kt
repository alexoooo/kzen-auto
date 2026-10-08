package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import java.io.OutputStream


/**
 * A chunk of encoded bytes, such as the records `Format` emits: what `Write` puts in a file. Like [Content] it
 * exposes no properties, so it travels as one opaque native rather than a record of its members, and it is read
 * only through its methods, inside the callback that received it.
 *
 * It is pooled ([PooledBytes]): once the callback returns, the same object may already hold the next chunk. A
 * consumer that needs the bytes later copies them (with [writeTo]) before its callback returns.
 *
 * The chunk that starts an output tells its consumer two things: which of its bytes are the output's header
 * ([headerLength], [copyHeader]), so the consumer can start that output again itself, and whether its producer gave
 * up the output it had open ([restartsOutput]: a live edit changed how it encodes), so what the consumer holds of
 * that output must not be published.
 */
class Bytes internal constructor() {
    private val buffer = DataRecordBuffer()
    private var headerLength = 0
    private var restartsOutput = false


    fun length(): Int =
        buffer.bytesLength


    fun writeTo(output: OutputStream) {
        output.write(buffer.bytes, 0, buffer.bytesLength)
    }


    /** The storage a producer encodes into: the byte side of a [DataRecordBuffer]. */
    internal fun buffer(): DataRecordBuffer =
        buffer


    /** Set by the producer for every chunk it fills; zero and false for a chunk that continues an output. */
    internal fun mark(headerLength: Int, restartsOutput: Boolean) {
        this.headerLength = headerLength
        this.restartsOutput = restartsOutput
    }


    internal fun headerLength(): Int =
        headerLength


    internal fun restartsOutput(): Boolean =
        restartsOutput


    /** A copy of the header this chunk begins with; empty for a chunk that continues an output. */
    internal fun copyHeader(): ByteArray =
        buffer.bytes.copyOf(headerLength)
}
