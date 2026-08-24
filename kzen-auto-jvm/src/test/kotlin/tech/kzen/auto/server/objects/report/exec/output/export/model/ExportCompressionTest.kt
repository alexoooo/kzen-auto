package tech.kzen.auto.server.objects.report.exec.output.export.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class ExportCompressionTest {
    @Test
    fun closeEntryFailureStillClosesTheStreamAndLaterCleanupDoesNotRepeatIt() {
        var entryCalls = 0
        var streamCalls = 0
        val closer = RetryingZipCloser(
            closeEntry = {
                entryCalls += 1
                error("injected closeEntry failure")
            },
            closeStream = { streamCalls += 1 })

        assertFailsWith<IllegalStateException> { closer.close() }
        assertEquals(1, entryCalls)
        assertEquals(1, streamCalls, "entry failure must not prevent the underlying close")

        closer.close()
        assertEquals(1, entryCalls, "a closed stream cannot safely retry its failed entry phase")
        assertEquals(1, streamCalls)
    }


    @Test
    fun successfulEntryIsNotRepeatedWhileUnderlyingCloseRetries() {
        var entryCalls = 0
        var streamCalls = 0
        val closer = RetryingZipCloser(
            closeEntry = { entryCalls += 1 },
            closeStream = {
                streamCalls += 1
                if (streamCalls == 1) error("injected stream close failure")
            })

        assertFailsWith<IllegalStateException> { closer.close() }
        assertEquals(1, entryCalls)
        assertEquals(1, streamCalls)

        closer.close()
        assertEquals(1, entryCalls, "the already-finalized entry must not be repeated")
        assertEquals(2, streamCalls)

        closer.close()
        assertEquals(1, entryCalls)
        assertEquals(2, streamCalls, "the composite close is idempotent after success")
    }


    @Test
    fun simultaneousPhaseFailuresPreserveTheEntryFailureAndSuppressTheStreamFailure() {
        val closer = RetryingZipCloser(
            closeEntry = { error("entry") },
            closeStream = { error("stream") })

        val failure = assertFailsWith<IllegalStateException> { closer.close() }
        assertEquals("entry", failure.message)
        assertEquals(listOf("stream"), failure.suppressed.map { it.message })
    }
}
