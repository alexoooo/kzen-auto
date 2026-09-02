package tech.kzen.auto.server.data.content


interface SequentialByteContent: AutoCloseable {
    /** Returns a positive count or `-1`; zero is reserved for zero-length requests. */
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int
}
