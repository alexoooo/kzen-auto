package tech.kzen.auto.server.data.content.provider

import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.server.data.content.SequentialByteContent


class DataContentHandle(
    val descriptor: DataContentDescriptor,
    val observedFingerprint: DataContentFingerprint,
    val bytes: SequentialByteContent
): AutoCloseable {
    private var closed = false


    override fun close() {
        if (closed) return
        closed = true
        bytes.close()
    }
}
