package tech.kzen.auto.server.data.read

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.read.CursorAdoptionIdentity
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.exec.data.value.DataValue


class OwnedReaderDataCursor(
    private val delegate: DataCursor,
    private val owner: AutoCloseable,
    override val adoptionIdentity: CursorAdoptionIdentity
): OperationalDataCursor {
    override val shape: DataShape get() = delegate.shape

    private var closed = false


    override fun hasNext(): Boolean {
        check(!closed) { "Data cursor is closed" }
        return try {
            delegate.hasNext().also { hasNext ->
                if (!hasNext) close()
            }
        }
        catch (failure: Throwable) {
            closeSuppressing(failure)
            throw failure
        }
    }


    override fun next(): DataValue {
        check(!closed) { "Data cursor is closed" }
        return try {
            delegate.next()
        }
        catch (failure: Throwable) {
            closeSuppressing(failure)
            throw failure
        }
    }


    override fun close() {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        try {
            delegate.close()
        }
        catch (closeFailure: Throwable) {
            failure = closeFailure
        }
        try {
            owner.close()
        }
        catch (closeFailure: Throwable) {
            if (failure == null) failure = closeFailure else failure.addSuppressed(closeFailure)
        }
        failure?.let { throw it }
    }


    private fun closeSuppressing(failure: Throwable) {
        try {
            close()
        }
        catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
    }
}
