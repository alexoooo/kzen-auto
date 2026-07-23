package tech.kzen.auto.client.objects.document.common.edit

import kotlinx.coroutines.delay
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.model.document.DocumentPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class DebouncedSubmitterTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val path = DocumentPath.parse("main.yaml")


    private fun fixture(): Pair<LogicValidationGlobal, DocumentEditActivity> {
        val logicValidationGlobal = LogicValidationGlobal()
        return logicValidationGlobal to DocumentEditActivity(logicValidationGlobal, path)
    }


    private fun busy(logicValidationGlobal: LogicValidationGlobal): Boolean {
        return logicValidationGlobal.summaryFor(path).busy
    }


    // Let the submit coroutine flush() kicked off run to its finally. The settle rides the same suspend hand-off a
    // real (I/O-suspending) commit does, so it lands on a later turn rather than synchronously with flush().
    private suspend fun settle() {
        delay(20)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun scheduleMarksBusyBeforeSubmit() {
        val (logicValidationGlobal, editActivity) = fixture()
        var submitCount = 0
        val submitter = DebouncedSubmitter(editActivity = { editActivity }) { submitCount++ }

        submitter.schedule()

        assertTrue(busy(logicValidationGlobal))
        assertEquals(0, submitCount)

        // Clear the still-pending debounce timer so it can't fire after the test.
        submitter.cancel()
    }


    @Test
    fun flushCommitsThenSettles() = async {
        val (logicValidationGlobal, editActivity) = fixture()
        var submitCount = 0
        val submitter = DebouncedSubmitter(editActivity = { editActivity }) { submitCount++ }

        submitter.schedule()
        submitter.flush()
        settle()

        assertEquals(1, submitCount)
        assertFalse(busy(logicValidationGlobal))
    }


    @Test
    fun cancelDiscardsPendingAndSettles() {
        val (logicValidationGlobal, editActivity) = fixture()
        var submitCount = 0
        val submitter = DebouncedSubmitter(editActivity = { editActivity }) { submitCount++ }

        submitter.schedule()
        submitter.cancel()

        assertEquals(0, submitCount)
        assertFalse(busy(logicValidationGlobal))
    }


    @Test
    fun reArmDuringCommitHoldsBusyUntilFollowUpSettles() = async {
        val (logicValidationGlobal, editActivity) = fixture()
        var submitCount = 0
        var reArmed = false
        lateinit var submitter: DebouncedSubmitter
        submitter = DebouncedSubmitter(editActivity = { editActivity }) {
            submitCount++
            if (!reArmed) {
                // A keystroke landing while this commit is in flight re-arms the debounce; its own commit settles.
                reArmed = true
                submitter.schedule()
            }
        }

        submitter.schedule()
        submitter.flush()
        settle()

        // The re-arm left the debounce pending, so the mark is held — busy stays through the hand-off.
        assertEquals(1, submitCount)
        assertTrue(busy(logicValidationGlobal))

        submitter.flush()
        settle()

        assertEquals(2, submitCount)
        assertFalse(busy(logicValidationGlobal))
    }


    @Test
    fun activitySwapBetweenKeystrokesReplantsMark() {
        val logicValidationGlobal = LogicValidationGlobal()
        val pathA = DocumentPath.parse("a.yaml")
        val pathB = DocumentPath.parse("b.yaml")
        var activity = DocumentEditActivity(logicValidationGlobal, pathA)

        val submitter = DebouncedSubmitter(editActivity = { activity }) {}

        submitter.schedule()
        assertTrue(logicValidationGlobal.summaryFor(pathA).busy)

        // A document switch that doesn't remount the editor swaps the bridge (and its activity) between
        // keystrokes; the next schedule must settle the old document's mark, not leave it stuck.
        activity = DocumentEditActivity(logicValidationGlobal, pathB)
        submitter.schedule()

        assertFalse(logicValidationGlobal.summaryFor(pathA).busy)
        assertTrue(logicValidationGlobal.summaryFor(pathB).busy)

        submitter.cancel()
        assertFalse(logicValidationGlobal.summaryFor(pathB).busy)
    }
}
