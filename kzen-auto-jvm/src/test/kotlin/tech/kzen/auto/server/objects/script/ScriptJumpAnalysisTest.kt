package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.objects.document.script.model.ScriptJumpAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * Locks [ScriptJumpAnalysis.plan] — the move-to (Set Next Statement) target surgery (execution-control phase 2):
 * validity (step vs binding vs branch group vs loop-body), the descend/ancestor set, the preceding-on-path skip
 * candidates, and the drop set. Reuses the ScriptNestingAnalysis fixture (ForEach -> DoWhile -> If, plus a
 * root-level two-branch If).
 */
class ScriptJumpAnalysisTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/script-nesting-test.yaml")


    private fun plan(targetPath: String): ScriptJumpAnalysis.ScriptJumpPlan {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).successful()
        val scriptTree = ScriptTree.read(documentPath, graphDefinition)
        return ScriptJumpAnalysis.plan(
            graphNotation, documentPath, scriptTree, ObjectPath.parse(targetPath))
    }


    private fun path(objectPath: String) = ObjectPath.parse(objectPath)


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun rootStepIsValidWithNoAncestors() {
        val jumpPlan = plan("main.steps/RootStep")
        assertTrue(jumpPlan.valid)
        assertEquals(listOf(), jumpPlan.ancestors)
        // predecessors are used as a set (skip-set membership); order is unspecified.
        assertEquals(setOf(path("main.steps/Flag")), jumpPlan.precedingOnPath.toSet())
        assertTrue(path("main.steps/RootStep") in jumpPlan.dropSet)
        assertTrue(path("main.steps/OuterLoop") in jumpPlan.dropSet)
        assertFalse(path("main.steps/Flag") in jumpPlan.dropSet)
    }


    @Test
    fun loopStepItselfIsValidAndDropsItsBody() {
        val jumpPlan = plan("main.steps/OuterLoop")
        assertTrue(jumpPlan.valid)
        assertEquals(listOf(), jumpPlan.ancestors)
        assertEquals(
            setOf(
                path("main.steps/Flag"),
                path("main.steps/RootStep"),
                path("main.steps/OuterRange")),
            jumpPlan.precedingOnPath.toSet())
        assertTrue(path("main.steps/OuterLoop") in jumpPlan.dropSet)
        // the loop body restarts at iteration 0: its nested steps are dropped
        assertTrue(
            path("main.steps/OuterLoop.steps/InnerLoop.steps/Branch.branches/Branch.steps/DeepStep") in jumpPlan.dropSet)
        assertFalse(path("main.steps/OuterRange") in jumpPlan.dropSet)
    }


    @Test
    fun ifBranchStepIsValidWithTheIfAsDescendAncestor() {
        val jumpPlan = plan("main.steps/TopIf.branches/Branch.steps/ThenStep")
        assertTrue(jumpPlan.valid)
        assertEquals(listOf(path("main.steps/TopIf")), jumpPlan.ancestors)
        // the descend ancestor and the target itself are dropped (the If re-runs its condition on rebuild)
        assertTrue(path("main.steps/TopIf") in jumpPlan.dropSet)
        assertTrue(path("main.steps/TopIf.branches/Branch.steps/ThenStep") in jumpPlan.dropSet)
    }


    @Test
    fun loopBodyStepIsInvalid() {
        val jumpPlan = plan("main.steps/OuterLoop.steps/InnerLoop.steps/Branch.branches/Branch.steps/DeepStep")
        assertFalse(jumpPlan.valid)
        assertTrue(jumpPlan.invalidReason!!.contains("loop body"))
    }


    @Test
    fun ifBranchGroupItselfIsInvalid() {
        // The IfBranch is a structural group node — condition + steps, never executed — so the run can no more
        // park at it than at a loop-item binding.
        val jumpPlan = plan("main.steps/TopIf.branches/Branch 2")
        assertFalse(jumpPlan.valid)
        assertTrue(jumpPlan.invalidReason!!.contains("branch"))
    }


    @Test
    fun aStepInALaterBranchStillDescendsThroughTheIfAlone() {
        // The path to a second-branch step crosses an IfBranch group node, which must not join the descend set:
        // only the If itself is a container step the rebuilt spine can re-run.
        val jumpPlan = plan("main.steps/TopIf.branches/Branch 2.steps/ElseIfStep")
        assertTrue(jumpPlan.valid)
        assertEquals(listOf(path("main.steps/TopIf")), jumpPlan.ancestors)
    }


    @Test
    fun loopItemBindingIsInvalid() {
        val jumpPlan = plan("main.steps/OuterLoop.item/OuterItem")
        assertFalse(jumpPlan.valid)
        assertTrue(jumpPlan.invalidReason!!.contains("binding"))
    }


    @Test
    fun unknownTargetIsInvalid() {
        assertFalse(plan("main.steps/DoesNotExist").valid)
    }
}
