package tech.kzen.auto.plugin.helper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test


/**
 * The slot-reuse contract is what this class exists for: [ListPipelineOutput.flush] rewinds the write cursor but
 * keeps the allocated slots, so a steady-state pipeline stops allocating entirely. Identity assertions pin that,
 * since a correct-looking implementation that reallocated per flush would satisfy every value assertion.
 */
class ListPipelineOutputTest {
    private class Slot {
        var value: String = ""
    }


    private fun output(): ListPipelineOutput<Slot> {
        return ListPipelineOutput { Slot() }
    }


    private fun drain(output: ListPipelineOutput<Slot>): List<String> {
        val drained = mutableListOf<String>()
        output.flush { drained.add(it.value) }
        return drained
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun nextWithoutCommitKeepsHandingBackTheSameSlot() {
        val output = output()

        assertSame(output.next(), output.next())
    }


    @Test
    fun flushDrainsCommittedSlotsInCommitOrder() {
        val output = output()

        for (value in listOf("a", "b", "c")) {
            output.next().value = value
            output.commit()
        }

        assertEquals(listOf("a", "b", "c"), drain(output))
    }


    @Test
    fun anUncommittedSlotIsNotDrained() {
        val output = output()

        output.next().value = "committed"
        output.commit()
        output.next().value = "abandoned"

        assertEquals(listOf("committed"), drain(output))
    }


    @Test
    fun flushOfAnUntouchedOutputVisitsNothing() {
        assertEquals(emptyList<String>(), drain(output()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun flushRewindsTheCursorSoTheNextFillStartsOver() {
        val output = output()

        output.next().value = "first"
        output.commit()
        drain(output)

        output.next().value = "second"
        output.commit()

        assertEquals(listOf("second"), drain(output))
    }


    @Test
    fun slotsAreReusedAcrossFlushesRatherThanReallocated() {
        val output = output()

        val firstRound = mutableListOf<Slot>()
        repeat(2) {
            firstRound.add(output.next())
            output.commit()
        }
        output.flush { }

        val secondRound = mutableListOf<Slot>()
        repeat(2) {
            secondRound.add(output.next())
            output.commit()
        }

        assertSame(firstRound[0], secondRound[0])
        assertSame(firstRound[1], secondRound[1])
    }


    @Test
    fun anAbandonedSlotIsHandedOutAgainStillCarryingItsStaleContent() {
        // Slots are recycled, not cleared — the producer overwrites every field it cares about. Pinned because a
        // producer that writes only *some* fields would silently inherit the previous occupant's values.
        val output = output()

        output.next().value = "abandoned"
        output.flush { }

        assertEquals("abandoned", output.next().value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun batchFillsAndCommitsEachSlot() {
        val output = output()
        var index = 0

        output.batch(3) { it.value = "item-${index++}" }

        assertEquals(listOf("item-0", "item-1", "item-2"), drain(output))
    }


    @Test
    fun batchOfZeroCommitsNothing() {
        val output = output()

        output.batch(0) { it.value = "unreachable" }

        assertEquals(emptyList<String>(), drain(output))
    }
}
