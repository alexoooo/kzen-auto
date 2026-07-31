package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.objects.document.script.model.ScriptJumpRefusal
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Locks the user-facing wording of a refused move-to (Set Next Statement) destination, and the classification
 * behind it. Both the drag handle and the server read these strings, and the user reads them in the run-control
 * error panel — so the exact sentence is the contract, not an implementation detail.
 *
 * Reuses the ScriptNestingAnalysis fixture (ForEach -> DoWhile -> If), whose nesting is what pins the
 * outermost-loop choice: naming the inner loop would advise a move that is itself refused.
 */
class ScriptJumpRefusalTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/script/structure/script-nesting-test.yaml")


    private fun reason(targetPath: String): String {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).successful()
        return ScriptJumpRefusal.reason(
            graphNotation, documentPath, ScriptTree.read(documentPath, graphDefinition),
            ObjectPath.parse(targetPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun loopBodyStepNamesTheOutermostLoop() {
        assertEquals(
            "That step is inside OuterLoop, which can't be sent to a different iteration. " +
                    "Move to OuterLoop itself to restart it.",
            reason("main.steps/OuterLoop.steps/InnerLoop.steps/Branch.branches/Branch.steps/DeepStep"))
    }


    @Test
    fun bindingIsNotAStepAtAll() {
        assertEquals(
            "That isn't a step this Script can jump to",
            reason("main.steps/OuterLoop.item/OuterItem"))
    }
}
