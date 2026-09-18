package tech.kzen.auto.client.service.rest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class RemoteApplyGateTest {
    @Test
    fun idleGateRunsTheBlockImmediately() {
        val gate = RemoteApplyGate()
        var ran = false

        gate.whenSettled { ran = true }

        assertTrue(ran)
    }


    @Test
    fun nestedWritesHoldUntilTheLastOneEnds() {
        val gate = RemoteApplyGate()
        val order = mutableListOf<String>()
        gate.begin()
        gate.begin()

        gate.whenSettled { order.add("first") }
        gate.whenSettled { order.add("second") }
        gate.end()
        assertFalse(gate.settled())
        assertEquals(emptyList(), order)

        gate.end()

        assertTrue(gate.settled())
        assertEquals(listOf("first", "second"), order)
    }


    @Test
    fun aCallbackStartingANewWriteDoesNotRerunTheOthersAndLaterBlocksWaitForIt() {
        val gate = RemoteApplyGate()
        var others = 0
        var late = 0
        gate.begin()
        gate.whenSettled { gate.begin() }
        gate.whenSettled { others++ }

        gate.end()
        gate.whenSettled { late++ }

        assertEquals(1, others)
        assertEquals(0, late)
        gate.end()
        assertEquals(1, others)
        assertEquals(1, late)
    }
}
