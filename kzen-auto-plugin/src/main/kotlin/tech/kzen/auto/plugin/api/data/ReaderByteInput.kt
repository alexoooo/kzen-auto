package tech.kzen.auto.plugin.api.data


/**
 * Provider-neutral, coding-decoded byte input supplied to a reader.
 *
 * The composition root retains ownership. A capability reads the input but cannot close the provider handle;
 * closing the returned cursor releases both the reader and the input.
 */
interface ReaderByteInput {
    val expandedBytesRead: Long

    /** Returns a positive count or `-1`; zero is reserved for zero-length requests. */
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int
}
