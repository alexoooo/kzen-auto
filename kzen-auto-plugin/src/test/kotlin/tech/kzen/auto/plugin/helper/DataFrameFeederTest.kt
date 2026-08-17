package tech.kzen.auto.plugin.helper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.data.DataBlockBuffer


/**
 * The feeder turns fixed-size blocks into whole records, so its whole job is the seam between them: a record split
 * across two blocks has to be stitched, and a block that contains nothing but the front half of one must emit
 * nothing at all. Both directions are pinned here, along with the end-of-data terminator.
 */
class DataFrameFeederTest {
    private class TestEvent: DataInputEvent()


    private class Emitted(val content: String, val endOfData: Boolean)


    private val blockSize = 32
    private val output = ListPipelineOutput<DataInputEvent> { TestEvent() }
    private val feeder = DataFrameFeeder(output)


    //-----------------------------------------------------------------------------------------------------------------
    private fun textBlock(
        content: String,
        vararg frameLengths: Int,
        partialLast: Boolean = false,
        endOfData: Boolean = false
    ): DataBlockBuffer {
        val block = DataBlockBuffer.ofText(blockSize)
        content.toCharArray().copyInto(block.chars)
        block.charsLength = content.length
        return withFrames(block, frameLengths, partialLast, endOfData)
    }


    private fun binaryBlock(
        content: String,
        vararg frameLengths: Int,
        partialLast: Boolean = false,
        endOfData: Boolean = false
    ): DataBlockBuffer {
        val block = DataBlockBuffer.ofBinary(blockSize)
        val bytes = content.toByteArray(Charsets.UTF_8)
        bytes.copyInto(block.bytes)
        block.bytesLength = bytes.size
        return withFrames(block, frameLengths, partialLast, endOfData)
    }


    private fun withFrames(
        block: DataBlockBuffer,
        frameLengths: IntArray,
        partialLast: Boolean,
        endOfData: Boolean
    ): DataBlockBuffer {
        var offset = 0
        for (frameLength in frameLengths) {
            block.frames.add(offset, frameLength)
            offset += frameLength
        }
        if (partialLast) {
            block.frames.setPartialLast()
        }
        block.endOfData = endOfData
        return block
    }


    private fun drain(): List<Emitted> {
        val drained = mutableListOf<Emitted>()
        output.flush { event ->
            val data = event.data
            val content =
                if (data.charsLength > 0) {
                    String(data.chars, 0, data.charsLength)
                }
                else {
                    String(data.bytes, 0, data.bytesLength, Charsets.UTF_8)
                }
            drained.add(Emitted(content, event.endOfData))
        }
        return drained
    }


    private fun drainContent(): List<String> {
        return drain().map { it.content }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun everyCompleteFrameInABlockBecomesARecord() {
        val emitted = feeder.feed(textBlock("abcdefgh", 4, 4))

        assertEquals(2, emitted)
        assertEquals(listOf("abcd", "efgh"), drainContent())
    }


    @Test
    fun binaryFramesTravelOnTheByteSide() {
        val emitted = feeder.feed(binaryBlock("01234567", 4, 4))

        assertEquals(2, emitted)
        assertEquals(listOf("0123", "4567"), drainContent())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aTrailingPartialFrameIsHeldBackAndStitchedOntoTheNextBlock() {
        assertEquals(1, feeder.feed(textBlock("abcdef", 4, 2, partialLast = true)))
        assertEquals(listOf("abcd"), drainContent())

        // "ef" was withheld; this block's leading "gh" completes it before its own whole frame follows.
        assertEquals(2, feeder.feed(textBlock("ghijkl", 2, 4)))
        assertEquals(listOf("efgh", "ijkl"), drainContent())
    }


    @Test
    fun aBlockHoldingOnlyTheFrontOfARecordEmitsNothing() {
        assertEquals(0, feeder.feed(textBlock("abc", 3, partialLast = true)))
        assertEquals(emptyList<String>(), drainContent())

        assertEquals(1, feeder.feed(textBlock("def", 3)))
        assertEquals(listOf("abcdef"), drainContent())
    }


    @Test
    fun aRecordCanSpanThreeBlocks() {
        assertEquals(0, feeder.feed(textBlock("ab", 2, partialLast = true)))
        assertEquals(0, feeder.feed(textBlock("cd", 2, partialLast = true)))
        assertEquals(1, feeder.feed(textBlock("ef", 2)))

        assertEquals(listOf("abcdef"), drainContent())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun anEmptyEndOfDataBlockEmitsOnlyTheTerminator() {
        // The block carries no frames at all. DataFrameBuffer.hasFull() answers true for an empty buffer precisely
        // so this lands here rather than being mistaken for a partial record — see its own note.
        val emitted = feeder.feed(textBlock("", endOfData = true))

        assertEquals(0, emitted)
        val drained = drain()
        assertEquals(1, drained.size)
        assertTrue(drained[0].endOfData)
        assertEquals("", drained[0].content)
    }


    @Test
    fun aFinalBlockCarriesItsRecordsAheadOfTheTerminator() {
        val emitted = feeder.feed(textBlock("abcd", 4, endOfData = true))

        assertEquals(1, emitted)
        val drained = drain()
        assertEquals(listOf("abcd", ""), drained.map { it.content })
        assertEquals(listOf(false, true), drained.map { it.endOfData })
    }


    @Test
    fun endOfDataCannotArriveWithARecordStillUnfinished() {
        // A half-record with nothing following it is unrepresentable input, not a state to recover from.
        assertThrows(IllegalStateException::class.java) {
            feeder.feed(textBlock("abcdef", 4, 2, partialLast = true, endOfData = true))
        }
    }
}
