package tech.kzen.auto.server.exec

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tech.kzen.auto.common.paradigm.logic.RunTiming

class RunElapsedTimeTest {
    @Test fun durationIncludesIdleTimeAndFreezesAtFirstFinish() {
        var nanos = 100L
        val clock = RunElapsedTime { nanos }
        nanos += 5_000_000_000L
        assertEquals(5000L, clock.snapshot("first").elapsedMillis)
        assertFalse(clock.snapshot("first").settled)
        clock.finish()
        nanos += 7_000_000_000L
        clock.finish()
        val terminal = clock.snapshot("first")
        assertTrue(terminal.settled)
        assertEquals(5000L, terminal.elapsedMillis)
        assertEquals(terminal, RunTiming.ofCollection(terminal.toCollection()))
        assertEquals(0L, RunElapsedTime { nanos }.snapshot("second").elapsedMillis)
    }
}
