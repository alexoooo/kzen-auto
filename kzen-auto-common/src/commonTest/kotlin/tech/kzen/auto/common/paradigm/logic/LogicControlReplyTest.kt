package tech.kzen.auto.common.paradigm.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class LogicControlReplyTest {
    @Test
    fun reasonlessIsTheBareName() {
        assertEquals("Submitted", LogicControlReply(LogicRunResponse.Submitted).asString())

        val parsed = LogicControlReply.parse("Submitted")
        assertEquals(LogicRunResponse.Submitted, parsed.response)
        assertNull(parsed.reason)
    }


    @Test
    fun reasonRoundTrips() {
        val reply = LogicControlReply(LogicRunResponse.Rejected, "Inside a loop body (not supported)")
        assertEquals(reply, LogicControlReply.parse(reply.asString()))
    }


    @Test
    fun reasonKeepsItsOwnSeparators() {
        val reply = LogicControlReply(LogicRunResponse.Rejected, "Unable to compile Main: no such step")
        assertEquals(reply, LogicControlReply.parse(reply.asString()))
    }
}
