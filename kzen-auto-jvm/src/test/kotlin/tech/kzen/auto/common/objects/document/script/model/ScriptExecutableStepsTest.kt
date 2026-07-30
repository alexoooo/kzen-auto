package tech.kzen.auto.common.objects.document.script.model

import org.junit.Test
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals


/**
 * [ScriptNestingAnalysis.orderedExecutableStepPaths] is what the Script's execution margin bands: every step
 * the engine can stop at, in document order, and nothing that merely looks like one. The two things it must get
 * right are BINDING EXCLUSION (a `parameters` / `item` entry is a `ScriptStep` subtype but is never walked by
 * the spine, so it gets no breakpoint band) and PARENT-BEFORE-DESCENDANT order, which the margin's nearest-anchor
 * drop hit-test relies on.
 */
class ScriptExecutableStepsTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun loopItemBindingIsNotAnExecutableStep() {
        val documentPath = DocumentPath.parse("test/script/control/foreach-item-binding-test.yaml")

        assertEquals(
            listOf(
                "main.steps/Range",
                "main.steps/Loop",
                "main.steps/Loop.steps/Doubled",
                "main.steps/Result"),
            executableSteps(documentPath),
            "the ForEach's `item` binding is a ScriptStep subtype, but not a step the spine walks")
    }


    @Test
    fun scriptParameterIsNotAnExecutableStep() {
        val documentPath = DocumentPath.parse("test/script/structure/code-reference-rename-test.yaml")

        assertEquals(
            listOf(
                "main.steps/Source",
                "main.steps/Derived",
                "main.steps/My Source",
                "main.steps/Backtick User",
                "main.steps/String Literal",
                "main.steps/Thresholded"),
            executableSteps(documentPath),
            "`main.parameters/threshold` is a named value, not a step")
    }


    @Test
    fun branchesOfAnUnknownStepTypeAreDiscoveredFromMetadata() {
        val documentPath = DocumentPath.parse("test/script/structure/script-branch-discovery-test.yaml")

        // Notation-driven: TestSwitchStep has no backing class and no entry in any kzen dispatch, so its two
        // branches are reached purely through `is: List, of: ScriptStep`. Each container precedes its own body.
        assertEquals(
            listOf(
                "main.steps/Switch",
                "main.steps/Switch.caseA/ProduceA",
                "main.steps/Switch.caseB/UseA",
                "main.steps/Loop",
                "main.steps/Loop.steps/Body"),
            executableSteps(documentPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun executableSteps(documentPath: DocumentPath): List<String> {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        return ScriptNestingAnalysis
            .orderedExecutableStepPaths(
                graphNotation,
                documentPath,
                ScriptTree.read(documentPath, graphDefinitionAttempt.successful()))
            .map { it.asString() }
    }
}
