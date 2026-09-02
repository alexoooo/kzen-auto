package tech.kzen.auto.server.data.content


interface SequentialCharacterContent: AutoCloseable {
    val resolvedCharsetName: String
    val inspectionRecordLimit: Long
    val expandedBytesRead: Long? get() = null
    fun read(buffer: CharArray, offset: Int = 0, length: Int = buffer.size - offset): Int
}
