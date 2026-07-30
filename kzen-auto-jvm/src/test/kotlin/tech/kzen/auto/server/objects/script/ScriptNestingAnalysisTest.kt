package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.objects.document.script.model.ScriptNestingAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Locks [ScriptNestingAnalysis.enclosingLoops] — the notation-driven enclosing-loop enumeration ControlStep
 * validation (execution-control phase XC4) relies on: rerun-flag detection over both loop types (ForEach /
 * DoWhile), innermost-first ordering, and exclusion of If branches and the document root (which do not re-run).
 */
class ScriptNestingAnalysisTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/script/structure/script-nesting-test.yaml")


    private fun enclosingLoops(targetPath: String): List<ObjectLocation> {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).successful()
        val scriptTree = ScriptTree.read(documentPath, graphDefinition)
        return ScriptNestingAnalysis.enclosingLoops(
            graphNotation, documentPath, scriptTree, ObjectPath.parse(targetPath))
    }


    private fun loc(path: String) = ObjectLocation(documentPath, ObjectPath.parse(path))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun rootStepHasNoEnclosingLoops() {
        assertEquals(listOf(), enclosingLoops("main.steps/RootStep"))
    }


    @Test
    fun outerLoopHasNoEnclosingLoops() {
        assertEquals(listOf(), enclosingLoops("main.steps/OuterLoop"))
    }


    @Test
    fun innerLoopIsEnclosedByTheOuterLoop() {
        assertEquals(
            listOf(loc("main.steps/OuterLoop")),
            enclosingLoops("main.steps/OuterLoop.steps/InnerLoop"))
    }


    @Test
    fun deepStepIsEnclosedInnermostFirstSkippingTheIfBranch() {
        assertEquals(
            listOf(
                loc("main.steps/OuterLoop.steps/InnerLoop"),
                loc("main.steps/OuterLoop")),
            enclosingLoops("main.steps/OuterLoop.steps/InnerLoop.steps/Branch.branches/Branch.steps/DeepStep"))
    }
}
