package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.api.managed.PipelineOutput
import tech.kzen.auto.plugin.model.DataBlockBuffer
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.RecordDataBuffer


class ProcessorFrameFeeder(
        private val output: PipelineOutput<DataInputEvent>
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val partialInput = RecordDataBuffer()


    //-----------------------------------------------------------------------------------------------------------------
    fun feed(
            dataBlockBuffer: DataBlockBuffer
    ) {
        val frames = dataBlockBuffer.frames
        val continuingPartial = partialInput.length() > 0

        if (continuingPartial) {
            partialInput.addFrame(dataBlockBuffer, 0)
        }

        if (! frames.hasFull()) {
            check(! dataBlockBuffer.endOfData)
            if (! continuingPartial) {
                partialInput.addFrame(dataBlockBuffer, 0)
            }
            return
        }
        else if (continuingPartial) {
            val next = output.next()
            next.data.copy(partialInput)
            output.commit()
            partialInput.clear()
        }

        val firstComplete = if (continuingPartial) { 1 } else { 0 }
        val lastPartialCount = if (frames.partialLast) { 1 } else { 0 }
        val lastComplete = frames.count - lastPartialCount - 1

        for (i in firstComplete .. lastComplete) {
            val next = output.next()
            next.data.setFrame(dataBlockBuffer, i)
            output.commit()
        }

        if (frames.partialLast) {
            check(! dataBlockBuffer.endOfData)
            partialInput.addFrame(dataBlockBuffer, frames.count - 1)
        }
    }
}