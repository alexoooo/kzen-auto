package tech.kzen.auto.server.data.read.text

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.exec.data.value.DataValue


class PlainTextDataCursor(
    private val reader: PlainTextReader,
    override val shape: DataShape
): DataCursor {
    private var buffered: DataValue? = null
    private var exhausted = false
    private var closed = false

    override fun hasNext(): Boolean {
        check(!closed) { "Plain-text cursor is closed" }
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
        return requireNotNull(buffered).also { buffered = null }
    }

    override fun close() {
        if (closed) return
        closed = true
        buffered = null
        reader.close()
    }
}
