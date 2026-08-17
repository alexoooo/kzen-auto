package tech.kzen.auto.common.paradigm.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * Pins that [LogicConventions.isMissingError] recognizes exactly the messages [LogicConventions] itself
 * builds — the property that keeps the classifier from drifting away from the wording, since the failure
 * reaches the client as a bare message with no code to switch on.
 */
class LogicConventionsTest {
    private val runId = LogicRunId("run-1")
    private val otherRunId = LogicRunId("run-2")


    private fun isMissing(errorMessage: String): Boolean {
        return LogicConventions.isMissingError(errorMessage, runId)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aRunThatIsNoLongerActiveIsMissing() {
        assertTrue(isMissing(LogicConventions.notRunningError()))
    }


    @Test
    fun aRunReplacedByAnotherIsMissing() {
        assertTrue(isMissing(LogicConventions.wrongRunningError(runId, otherRunId)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aVanishedNodeIsNotMissing() {
        // RunEngine's wording for a request against a node with no handler. Deliberately NOT swallowed: an
        // unregistered handler produces the identical message, and hiding it would mask a wiring defect.
        assertFalse(isMissing("No request handler for node: n3"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aRefusalAimedAtSomeOtherRunIsNotMissing() {
        // The caller asked for runId; this message says a DIFFERENT run was the one asked for, so it answers
        // nothing about the caller's own request.
        assertFalse(isMissing(LogicConventions.wrongRunningError(otherRunId, runId)))
    }


    @Test
    fun anOrdinaryFailureIsNotMissing() {
        assertFalse(isMissing("Summary failed"))
        assertFalse(isMissing(""))
    }
}
