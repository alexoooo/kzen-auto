package tech.kzen.auto.common.objects.document.script.model

import org.junit.Test
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertTrue


class ScriptDependencyAnalysisTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/code-reference-rename-test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun detectsPlainAndBacktickedReferencesButNotStrings() {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val analysis = ScriptDependencyAnalysis.analyze(graphDefinitionAttempt, documentPath)

        // plain identifier reference (`Source + 1`)
        assertTrue(
            ScriptStepDependency(location("main.steps/Source"), location("main.steps/Derived")) in analysis.edges)

        // back-ticked reference (`` `My Source` + 2 ``) — the old plain-identifier regex missed this
        assertTrue(
            ScriptStepDependency(location("main.steps/My Source"), location("main.steps/Backtick User")) in analysis.edges)

        // an object name that only appears inside a string literal is not a dependency
        assertTrue(analysis.edges.none { it.target == location("main.steps/String Literal") })
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(objectPath: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse(objectPath))
    }
}
