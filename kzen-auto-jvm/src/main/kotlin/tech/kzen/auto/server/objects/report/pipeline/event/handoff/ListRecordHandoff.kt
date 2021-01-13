package tech.kzen.auto.server.objects.report.pipeline.event.handoff

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordMapBuffer


class ListRecordHandoff: RecordHandoff {
    //-----------------------------------------------------------------------------------------------------------------
    private val records = mutableListOf<RecordMapBuffer>()
    private var nextIndex = 0


    //-----------------------------------------------------------------------------------------------------------------
    override fun next(): RecordMapBuffer {
        val next =
            if (records.size > nextIndex) {
                records[nextIndex]
            }
            else {
                val addend = RecordMapBuffer()
                records.add(addend)
                addend
            }

        nextIndex++

        return next
    }


    override fun commit() {}


    //-----------------------------------------------------------------------------------------------------------------
    fun flush(visitor: (RecordMapBuffer) -> Unit) {
        for (i in 0 until nextIndex) {
            visitor.invoke(records[i])
        }
        nextIndex = 0
    }


    fun flush(): List<RecordMapBuffer> {
        val subList =
            if (records.size == nextIndex + 1) {
                records
            }
            else {
                records.subList(0, nextIndex)
            }

        nextIndex = 0

        return subList
    }
}