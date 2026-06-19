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
}