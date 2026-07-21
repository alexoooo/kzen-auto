package tech.kzen.auto.common.objects.document.script.model

import org.junit.Test
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * Branch discovery is driven by attribute metadata (`is: List, of: ScriptStep`) rather than a hardcoded
 * [steps, then, else] list, so a branching step type shared code has never heard of participates fully. The
 * fixture's TestSwitchStep is exactly that: two branches, no backing class, no entry in any kzen dispatch.
 */
class ScriptBranchDiscoveryTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/script-branch-discovery-test.yaml")

    private val switchLocation = location("main.steps/Switch")
    private val loopLocation = location("main.steps/Loop")
    private val bodyLocation = location("main.steps/Loop.steps/Body")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unknownStepTypesBranchesAreDiscoveredFromMetadata() {
        val analysis = analyze()

        assertEquals(
            AttributeLocation(switchLocation, attributePath("caseA")),
            analysis.branchOfStep[location("main.steps/Switch.caseA/ProduceA")],
            "caseA must be discovered as a branch of the unknown step type")

        assertEquals(
            AttributeLocation(switchLocation, attributePath("caseB")),
            analysis.branchOfStep[location("main.steps/Switch.caseB/UseA")],
            "caseB must be discovered as a branch of the unknown step type")
    }


    @Test
    fun referenceAcrossDiscoveredBranchesIsACrossBranchEdge() {
        val analysis = analyze()

        val edge = ScriptStepDependency(
            location("main.steps/Switch.caseA/ProduceA"),
            location("main.steps/Switch.caseB/UseA"))

        assertTrue(edge in analysis.edges, "the caseA -> caseB reference must produce an edge")
        assertTrue(edge in analysis.crossBranchEdges(), "the two cases are distinct branches of one container")
    }


    @Test
    fun productionLoopBodyStillClassifies() {
        val analysis = analyze()

        assertEquals(
            AttributeLocation(loopLocation, ScriptConventions.stepsAttributePath),
            analysis.branchOfStep[bodyLocation],
            "DoWhileStep's `steps` must be discovered through the archetype's metadata")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun branchAttributeNamesReflectEachTypesDeclaration() {
        val graphNotation = AutoTestUtils.readNotation()

        assertEquals(
            listOf(AttributeName("caseA"), AttributeName("caseB")),
            branchNames(graphNotation, switchLocation).sortedBy { it.value })

        assertEquals(
            listOf(ScriptConventions.stepsAttributeName),
            branchNames(graphNotation, loopLocation))

        // The root Script declares `steps` (`of: ScriptStep`) and `parameters` (`of: ParameterBinding`); only the
        // former is a body branch, even though ParameterBinding is itself `is: ScriptStep`.
        assertEquals(
            listOf(ScriptConventions.stepsAttributeName),
            branchNames(graphNotation, documentPath.toMainObjectLocation()))

        assertEquals(
            listOf(),
            branchNames(graphNotation, bodyLocation),
            "a leaf step declares no branches")
    }


    @Test
    fun bodyScopedExpressionIsReadThroughTheInheritanceChain() {
        val graphNotation = AutoTestUtils.readNotation()

        assertTrue(
            ScriptConventions.isBodyScopedExpression(
                graphNotation, loopLocation, ScriptConventions.conditionAttributeName),
            "DoWhileStep.condition declares `scope: body` on its archetype")

        assertFalse(
            ScriptConventions.isBodyScopedExpression(
                graphNotation, bodyLocation, AttributeName("code")),
            "FormulaStep.code is default-scoped (predecessors)")

        assertFalse(
            ScriptConventions.isBodyScopedExpression(
                graphNotation, loopLocation, ScriptConventions.stepsAttributeName),
            "the marker applies to expression attributes, not to the body branch itself")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun analyze(): ScriptDependencyAnalysis {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)
        return ScriptDependencyAnalysis.analyze(graphDefinitionAttempt.successful(), documentPath)
    }


    private fun branchNames(graphNotation: GraphNotation, objectLocation: ObjectLocation): List<AttributeName> {
        return ScriptConventions.stepBranchAttributeNames(graphNotation, objectLocation)
    }


    private fun attributePath(attributeName: String): AttributePath {
        return AttributePath.ofName(AttributeName(attributeName))
    }


    private fun location(objectPath: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse(objectPath))
    }
}
