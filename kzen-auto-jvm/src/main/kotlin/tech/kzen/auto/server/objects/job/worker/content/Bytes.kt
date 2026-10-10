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
 * Which output a chunk belongs to is in its metadata; how that output starts and ends is marked here, by its
 * producer:
 * - the chunk that starts an output says which of its bytes are the output's header ([headerLength],
 *   [copyHeader]), so the consumer can start that output again itself;
 * - the chunk that ends an output holds its footer, possibly no bytes ([endsOutput]): the output is complete;
 * - a chunk of no bytes may instead discard the output ([discardsOutput]: a live edit changed how its producer
 *   encodes), so what the consumer holds of it must not be published.
 */
class Bytes internal constructor() {
    private val buffer = DataRecordBuffer()
    private var headerLength = 0
    private var discardsOutput = false
    private var endsOutput = false


    fun length(): Int =
        buffer.bytesLength


    fun writeTo(output: OutputStream) {
        output.write(buffer.bytes, 0, buffer.bytesLength)
    }


    /** The storage a producer encodes into: the byte side of a [DataRecordBuffer]. */
    internal fun buffer(): DataRecordBuffer =
        buffer


    /** Set by the producer for every chunk it fills; zero and false for a chunk inside an output. */
    internal fun mark(headerLength: Int, discardsOutput: Boolean, endsOutput: Boolean) {
        this.headerLength = headerLength
        this.discardsOutput = discardsOutput
        this.endsOutput = endsOutput
    }


    internal fun headerLength(): Int =
        headerLength


    internal fun discardsOutput(): Boolean =
        discardsOutput


    internal fun endsOutput(): Boolean =
        endsOutput


    /** A copy of the header this chunk begins with; empty for a chunk inside an output. */
    internal fun copyHeader(): ByteArray =
        buffer.bytes.copyOf(headerLength)
}
