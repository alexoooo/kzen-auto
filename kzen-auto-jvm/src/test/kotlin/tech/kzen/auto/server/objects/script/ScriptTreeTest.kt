package tech.kzen.auto.server.objects.script

import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals


class ScriptTreeTest {
    //-----------------------------------------------------------------------------------------------------------------
    // Step order (and therefore branch predecessors) is derived from the document position of the step objects.
    @Test
    fun treeOrder() {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val documentPath = DocumentPath.parse("test/script-tree-test.yaml")

        val tree = ScriptTree.read(documentPath, graphDefinitionAttempt.successful())

        assertEquals(
            listOf(),
            tree.predecessors(
                ObjectPath.parse("main.steps/Formula")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula")),
            tree.predecessors(
                ObjectPath.parse("main.steps/ForEach")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula")),
            tree.predecessors(
                ObjectPath.parse("main.steps/ForEach.steps/Item")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula"),
                ObjectPath.parse("main.steps/ForEach.steps/Item")),
            tree.predecessors(
                ObjectPath.parse("main.steps/ForEach.steps/Is divisible by")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula"),
                ObjectPath.parse("main.steps/ForEach")),
            tree.predecessors(
                ObjectPath.parse("main.steps/Display")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Sibling branches of an If chain are ALTERNATIVES, not predecessors: when a later branch runs, the earlier
     * ones did not, so neither an earlier branch group nor its steps may appear in scope. This one rule is what
     * every consumer of the scope set reads — the branch condition's step select, the in-branch expression
     * editors, the server's expression scoping, the rename rewriter and the jump analysis — so it is asserted
     * here on the shared ScriptTree rather than per consumer.
     */
    @Test
    fun siblingBranchesOfAnIfChainAreNotInEachOthersScope() {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val documentPath = DocumentPath.parse("test/script-nesting-test.yaml")
        val tree = ScriptTree.read(documentPath, graphDefinitionAttempt.successful())

        // The root steps preceding TopIf — everything a step or condition inside ANY of its branches may read.
        val beforeTheIf = setOf(
            ObjectPath.parse("main.steps/Flag"),
            ObjectPath.parse("main.steps/RootStep"),
            ObjectPath.parse("main.steps/OuterRange"),
            ObjectPath.parse("main.steps/OuterLoop"))

        // The second branch's CONDITION editor: the select offers the steps before the If, never branch 1.
        assertEquals(
            beforeTheIf,
            tree.predecessors(ObjectPath.parse("main.steps/TopIf.branches/Branch 2")).toSet())

        // A step inside the second branch: same answer — branch 1's group node and its ThenStep are excluded.
        assertEquals(
            beforeTheIf,
            tree.predecessors(ObjectPath.parse("main.steps/TopIf.branches/Branch 2.steps/ElseIfStep")).toSet())

        // ... and symmetrically for the first branch, which cannot see the second either.
        assertEquals(
            beforeTheIf,
            tree.predecessors(ObjectPath.parse("main.steps/TopIf.branches/Branch.steps/ThenStep")).toSet())

        // inScopeReferencePaths is predecessors + bindings; at the document root there are no Script parameters
        // and TopIf is outside every loop, so it adds nothing here.
        assertEquals(
            beforeTheIf,
            tree.inScopeReferencePaths(
                ObjectPath.parse("main.steps/TopIf.branches/Branch 2.steps/ElseIfStep")).toSet())
    }
}