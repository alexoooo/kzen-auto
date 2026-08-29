package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue
import java.util.ArrayDeque
import java.util.NoSuchElementException


class FileDataCursor(
    private val inputChain: ReportInputChain<*>,
    override val shape: DataShape,
    private val classLoaderHandle: ClassLoaderHandle
): DataCursor {
    private val header = FlatRecordHeader(shape.itemType)
    private val buffered = ArrayDeque<DataValue>()
    private var endOfInput = false
    private var closed = false


    override fun hasNext(): Boolean {
        fill()
        return buffered.isNotEmpty()
    }


    override fun next(): DataValue {
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
                    val record = event.row.prototype()
                    record.attachHeader(header)
                    buffered.addLast(DataValue(record, DataNode(0)))
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
