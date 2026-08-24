package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import java.util.ArrayDeque
import java.util.NoSuchElementException


class FileDataCursor(
    private val inputChain: ReportInputChain<*>,
    override val shape: DataShape.Tabular,
    private val classLoaderHandle: ClassLoaderHandle
): DataCursor {
    private val buffered = ArrayDeque<FlatFileRecord>()
    private var endOfInput = false
    private var closed = false


    override fun hasNext(): Boolean {
        fill()
        return buffered.isNotEmpty()
    }


    override fun next(): FlatFileRecord {
        fill()
        if (buffered.isEmpty()) {
            throw NoSuchElementException()
        }
        return buffered.removeFirst()
    }


    private fun fill() {
        check(!closed) { "Data cursor is closed" }
        while (buffered.isEmpty() && !endOfInput) {
            val hasMore = inputChain.poll { event ->
                if (!event.skip) {
                    buffered.addLast(event.row.prototype())
                }
            }
            if (!hasMore) {
                endOfInput = true
            }
        }
    }


    override fun close() {
        if (closed) {
            return
        }
        closed = true
        try {
            inputChain.close()
        }
        finally {
            classLoaderHandle.close()
        }
    }
}
