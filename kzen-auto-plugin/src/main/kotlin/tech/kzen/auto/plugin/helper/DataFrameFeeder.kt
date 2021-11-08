package tech.kzen.auto.plugin.helper

import tech.kzen.auto.plugin.api.managed.PipelineOutput
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.plugin.model.data.DataRecordBuffer


class DataFrameFeeder(
    private val output: PipelineOutput<DataInputEvent>
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val partialInput = DataRecordBuffer()


    //-----------------------------------------------------------------------------------------------------------------
    fun feed(
        dataBlockBuffer: DataBlockBuffer
    ): Int {
        val frames = dataBlockBuffer.frames
        val continuingPartial = partialInput.length() > 0

        if (continuingPartial) {
            partialInput.addFrame(dataBlockBuffer, 0)
        }

        var count = 0
        if (! frames.hasFull()) {
            check(! dataBlockBuffer.endOfData)
            if (! continuingPartial) {
                partialInput.addFrame(dataBlockBuffer, 0)
            }
            return 0
        }
        else if (continuingPartial) {
            val next = output.next()
            next.data.copy(partialInput)
            output.commit()
            partialInput.clear()
            count++
//            println("DataFrameFeeder - continuingPartial")
        }

        val firstComplete = if (continuingPartial) { 1 } else { 0 }
        val lastPartialCount = if (frames.partialLast) { 1 } else { 0 }
        val lastComplete = frames.count - lastPartialCount - 1

        for (i in firstComplete .. lastComplete) {
            val next = output.next()
            next.data.setFrame(dataBlockBuffer, i)
            output.commit()
            count++
//            println("DataFrameFeeder - commit - $count")
        }

        if (frames.partialLast) {
            check(! dataBlockBuffer.endOfData)
            partialInput.addFrame(dataBlockBuffer, frames.count - 1)
//            println("DataFrameFeeder - partialLast")
        }

        if (dataBlockBuffer.endOfData) {
            val next = output.next()
            next.data.clear()
            next.endOfData = true
            output.commit()
//            println("DataFrameFeeder - end of data")
        }

        return count
    }
}