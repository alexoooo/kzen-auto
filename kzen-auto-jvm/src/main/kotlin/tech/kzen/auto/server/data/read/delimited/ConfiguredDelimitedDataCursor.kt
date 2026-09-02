package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.exec.data.value.DataValue


class ConfiguredDelimitedDataCursor(
    private val reader: ConfiguredDelimitedReader,
    override val shape: DataShape,
    private val expandedBytes: () -> Long? = { reader.expandedBytesRead }
): DataCursor {
    val expandedBytesRead: Long? get() = expandedBytes()

    private var buffered: DelimitedRecord? = null
    private var exhausted = false
    private var closed = false


    override fun hasNext(): Boolean {
        check(!closed) { "Data cursor is closed" }
        if (buffered != null) return true
        if (exhausted) return false
        try {
            buffered = reader.read()
        }
        catch (failure: Throwable) {
            try {
                close()
            }
            catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
        exhausted = buffered == null
        return !exhausted
    }


    override fun next(): DataValue {
        if (!hasNext()) throw NoSuchElementException()
        val next = requireNotNull(buffered).value
        buffered = null
        return next
    }


    override fun close() {
        if (closed) return
        closed = true
        buffered = null
        reader.close()
    }
}
