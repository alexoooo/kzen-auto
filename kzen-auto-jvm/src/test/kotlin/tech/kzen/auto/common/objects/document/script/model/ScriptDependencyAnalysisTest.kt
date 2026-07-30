package tech.kzen.auto.common.objects.document.script.model

import org.junit.Test
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertTrue


class ScriptDependencyAnalysisTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/script/structure/code-reference-rename-test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun detectsPlainAndBacktickedReferencesButNotStrings() {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val analysis = ScriptDependencyAnalysis.analyze(graphDefinitionAttempt.successful(), documentPath)

        // plain identifier reference (`Source + 1`)
        assertTrue(
            ScriptStepDependency(location("main.steps/Source"), location("main.steps/Derived")) in analysis.edges)

        // back-ticked reference (`` `My Source` + 2 ``) — an escaped step name must still resolve to a dependency
        assertTrue(
            ScriptStepDependency(location("main.steps/My Source"), location("main.steps/Backtick User")) in analysis.edges)

        // an object name that only appears inside a string literal is not a dependency
        assertTrue(analysis.edges.none { it.target == location("main.steps/String Literal") })
    }


    @Test
    fun detectsParameterReferencedByStepAsCrossBranchEdge() {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val analysis = ScriptDependencyAnalysis.analyze(graphDefinitionAttempt.successful(), documentPath)

        // `threshold * 2` references the parameter by name: parameter is the source, the step is the target.
        val parameterEdge = ScriptStepDependency(
            location("main.parameters/threshold"), location("main.steps/Thresholded"))
        assertTrue(parameterEdge in analysis.edges)

        // the parameter lives in a different branch than the step, so it is drawn by the cross-branch overlay
        assertTrue(parameterEdge in analysis.crossBranchEdges())
    }


    @Test
    fun detectsLoopItemReferencedByBodyStepAsCrossBranchEdge() {
        val loopPath = DocumentPath.parse("test/script/control/foreach-item-binding-test.yaml")
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val analysis = ScriptDependencyAnalysis.analyze(graphDefinitionAttempt.successful(), loopPath)

        fun loopLocation(objectPath: String) =
            ObjectLocation(loopPath, ObjectPath.parse(objectPath))

        // `Item * 2` in the loop body references the ForEachItemBinding by name. The binding lives in the
        // ForEach's `item` branch, which stepBranchAttributeNames excludes by design (it holds a ScriptStep
        // SUBTYPE), so analyze walks it explicitly — without that the item is absent from branchOfStep and
        // classifyEdge silently drops this edge, leaving the loop item with no dependency line in the gutter.
        val itemEdge = ScriptStepDependency(
            loopLocation("main.steps/Loop.item/Item"), loopLocation("main.steps/Loop.steps/Doubled"))
        assertTrue(itemEdge in analysis.edges)

        // item branch vs steps branch, so it is drawn by the cross-branch overlay rather than an in-branch lane
        assertTrue(itemEdge in analysis.crossBranchEdges())
    }


    @Test
    fun aForEachItemsExpressionIsAnEdgeIntoTheLoop() {
        val loopPath = DocumentPath.parse("test/script/control/foreach-item-binding-test.yaml")
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val analysis = ScriptDependencyAnalysis.analyze(graphDefinitionAttempt.successful(), loopPath)

        fun loopLocation(objectPath: String) =
            ObjectLocation(loopPath, ObjectPath.parse(objectPath))

        // `items` is a Kotlin expression now, so this edge comes from the LEXICAL value-scalar scan rather
        // than from attributeReferencesIncludingWeak — the same handoff an IfBranch condition made. It has to
        // survive that move: ScriptValueReferences is built on these edges, and without it the Range's value
        // would be considered unread and elided out from under the loop.
        assertTrue(
            ScriptStepDependency(loopLocation("main.steps/Range"), loopLocation("main.steps/Loop"))
                    in analysis.edges)
    }


    @Test
    fun namesCollidingOnOneIdentifierAllBecomeSources() {
        val collisionPath = DocumentPath.parse("test/script/structure/script-name-collision-test.yaml")
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val analysis = ScriptDependencyAnalysis.analyze(graphDefinitionAttempt.successful(), collisionPath)

        fun collisionLocation(objectPath: String) =
            ObjectLocation(collisionPath, ObjectPath.parse(objectPath))

        val user = collisionLocation("main.steps/Branch.branches/Branch.steps/Shadow User")

        // `Shadowed + 1` cannot be attributed to one of the two same-named steps from its text, so BOTH get the
        // edge. Over-reporting is the safe direction: a dropped edge would let ScriptValueReferences call a value
        // unread when something reads it.
        assertTrue(
            ScriptStepDependency(collisionLocation("main.steps/Shadowed"), user) in analysis.edges,
            "the root-level `Shadowed` must be reported as a source")
        assertTrue(
            ScriptStepDependency(collisionLocation("main.steps/Branch.branches/Branch.steps/Shadowed"), user) in analysis.edges,
            "the branch-nested `Shadowed` must be reported as a source")
    }


    @Test
    fun anIfBranchConditionIsACrossBranchEdgeIntoTheBranchGroup() {
        val collisionPath = DocumentPath.parse("test/script/structure/script-name-collision-test.yaml")
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val analysis = ScriptDependencyAnalysis.analyze(graphDefinitionAttempt.successful(), collisionPath)

        fun collisionLocation(objectPath: String) =
            ObjectLocation(collisionPath, ObjectPath.parse(objectPath))

        // A branch's condition now lives on the IfBranch group object, not on the If — so the dependency runs
        // `Flag -> IfBranch`. The group child is reached only through the `group: true` walk; without it the
        // IfBranch is absent from branchOfStep, classifyEdge drops the edge, and the condition row loses its
        // dependency elbow in the gutter.
        val conditionEdge = ScriptStepDependency(
            collisionLocation("main.steps/Flag"),
            collisionLocation("main.steps/Branch.branches/Branch"))
        assertTrue(conditionEdge in analysis.edges)

        // root `steps` branch vs the If's `branches` group, so the overlay draws it as a cross-branch line
        assertTrue(conditionEdge in analysis.crossBranchEdges())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(objectPath: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse(objectPath))
    }
}
