package tech.kzen.auto.client.service.rest

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class NotationWriteBarrierTest {
    @Test
    fun idleBarrierDoesNotWait() {
        val barrier = NotationWriteBarrier()
        var ran = false

        start { barrier.awaitSettled(); ran = true }

        assertTrue(ran)
    }


    @Test
    fun nestedWritesHoldUntilTheLastOneEnds() {
        val barrier = NotationWriteBarrier()
        val order = mutableListOf<String>()
        barrier.begin()
        barrier.begin()

        start { barrier.awaitSettled(); order.add("first") }
        start { barrier.awaitSettled(); order.add("second") }
        barrier.end()
        assertEquals(emptyList(), order)

        barrier.end()

        assertTrue(barrier.settled())
        assertEquals(listOf("first", "second"), order)
    }


    @Test
    fun aWaiterThatStartsAWriteHoldsTheWaitersAfterIt() {
        val barrier = NotationWriteBarrier()
        var later = 0
        barrier.begin()
        start { barrier.awaitSettled(); barrier.begin() }
        start { barrier.awaitSettled(); later++ }

        barrier.end()
        assertEquals(0, later)

        barrier.end()
        assertEquals(1, later)
    }


    // Unconfined: runs to its first suspension at once and resumes inside end(), so the tests stay synchronous
    @OptIn(DelicateCoroutinesApi::class)
    private fun start(block: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.Unconfined) { block() }
    }
}
